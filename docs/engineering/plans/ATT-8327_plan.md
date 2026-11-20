# Stage 3 Implementation Plan: ATT-8327 - Process Stability 48-TST Verification

**Ticket**: [ATT-8327](https://rainerblind.atlassian.net/browse/ATT-8327) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Scope**: Executable Verification for 41 Automatable TSTs (`13` Bucket A + `8` Bucket B1 + `20` Bucket B2) + `7` Bucket C Retained `[Implemented]`
**Branch**: `feature/ATT-8327` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. SWE.2 Architecture, Guardrails & Scope
- **Layering & Test Isolation**: Pure test/verification additions under `tools/` (`test_jira_accounts.py`, `test_verify_requirement_governance.py`) and `app/src/test/` (Robolectric SQLite, `createComposeRule()`, JVM `.class` bytecode/reflection, unit tests).
- **Hard Guardrails**:
  1. `0` `app/src/main/` edits (unless a genuine defect is uncovered, in which case file a Backlog Bug under `Rule 5` and retain `[Implemented]`).
  2. `0` `app/src/main/` file reads in `app/src/test/` (`REQ-PRO-133`, `app/source-reading-baseline.txt == 105`, `LEGITIMATE_GOVERNANCE_GUARDS == 22`).
  3. `0` reflection-only `Class.forName("...Kt")` UI stubs (`REQ-PRO-132`, `app/ui-reflection-baseline.txt == 189`): all new Compose UI checks use Robolectric `createComposeRule()`.
  4. `0` fabricated evidence (`K1`/`M1`): Bucket C (`TST-MAN-001`, `TST-REG-001`, `TST-REG-003`, `TST-REG-008`, `TST-INT-007`, `TST-BUG-006`, `TST-STR-015`) remains `[Implemented]`.

---

## 2. Atomic Stage 4 Implementation Steps

| Step | Target File(s) | Atomic Action | Covers TSTs |
| :--- | :--- | :--- | :--- |
| **0** | Jira Gate 3 | Run `python3 tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>` (`exit 0`, `Rule 4`) before any code edits. | Gate 3 Lock |
| **1** | `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py` | Add/tag offline mocked Python `unittest` cases (`Rule 5`): tag `TestGateBypassPrevention`/`test_jira_epics.py` (`TST-PRO-009`), wrap `tools/verify_skills.verify_skills()` + transition guards (`TST-PRO-017`), and add `TST-PRO-003`, `TST-PRO-006`, `TST-PRO-007`, `TST-PRO-008`, `TST-PRO-010`, `TST-PRO-011` (`review_agent.py` mock), `TST-PRO-019` (`9 Core Rules` Triad parity), and `TST-PRO-020` (`AAA` + Stage 1/Rule 7.5/Ceremony 2). | `TST-PRO-003`, `006`–`011`, `017`, `019`, `020` (`10`) |
| **2** | Existing Kotlin suites in `app/src/test/` | Verify existing assertions & add explicit `TST-*` KDoc tags (+ minor missing assertions if needed): `BackupRestoreInteractionQueueTest` (`TST-PRO-004`), `WorkoutClustersViewModelTest` (`TST-BUG-001`), `AccumulatorTest`/`MyAccumulatorSensorTest` (`TST-REG-004`), `TrackerServiceStateMachineCharacterisationTest` (`TST-REG-007`), `EditWorkoutViewModelTest`/`SportTypeDatabaseManagerStravaTest` (`TST-INT-002`, `003`), `ArchitectureConformanceContractTest`/`TrackerServiceResumeTest` (`TST-STR-001`), `SensorAndDeviceConcurrencyStressTest` (`TST-STR-002`, `011`), `TrackerServiceIntegrationTest` (`TST-STR-004`), `SensorSourceDialogAltitudeCalibrationTest`/`AltitudeFromPressureDeviceTest` (`TST-BUG-007`). | `11` Bucket A Kotlin TSTs (`+2` Python in Step 1 = `13`) |
| **3a** | `app/src/test/` (Domain, DB & Contract) | Add deterministic unit / Robolectric SQLite / JVM `.class` bytecode tests (`0` `src/main/` reads): `WorkoutDataMapper` Extrema Pace min/max & Speed+Pace (`TST-LOG-001`, `002`); `PeriodSummariesDbTest` temporal gap sync (`TST-BUG-002`); `DevicesDatabaseManagerTest` + `AltitudeFromPressureDeviceTest` + `DeviceItem` icon/toggle/self-heal (`TST-BUG-008`–`011`); `TrackingViewsDatabaseManager` `onCreate`/`onUpgrade(v9->v10)` (`TST-STR-003`); `ATrainingTrackerMap` bytecode/caller contract (`TST-STR-005`); `AspiceSweAuditContractTest` Jira subtask automation & `docs/design_guidelines.md` §4 format-string hardening (`TST-STR-012`, `017`). | `12` Bucket B2 non-UI TSTs |
| **3b** | `app/src/test/` (`createComposeRule()` UI) | Add Robolectric `createComposeRule()` behavioral Compose UI tests (`REQ-PRO-132`): `MetricBadge`/`MetricItem` (`TST-STR-006`), `LocalMetricFormatter` (`TST-STR-007`), `MappableListItem` + `EmptyStatePlaceholder` (`TST-STR-008`), Elevation Profile toggle (`TST-INT-004`), narrow-width `TrackingTabConfigHeader` & checkbox sequence (`TST-INT-005`, `006`), Settings drawer routes (`TST-INT-008`), `PoweredByStrava` branding (`TST-REG-002`), and `MapDetailLayout` path context alpha (`TST-REG-005`). | `8` Bucket B2 Compose TSTs |
| **4** | Verification & Promotion (Stage 4/5) | Run `python3 -m unittest` on modified Python suites; run `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun --tests ...` on all modified/tagged Kotlin classes + `SourceTextReadingAuditContractTest` + `UiReflectionOnlyTestAuditContractTest` + `ArchitectureConformanceContractTest` + `DeadCodeEliminationContractTest` (NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`); promote verified TSTs (and REQs whose *all* linked TSTs are `[Verified]`) in Stage 5 and run `verify_requirement_governance.py --check-verified-evidence --sync-indexes`. | All `41` (`+7` `[Implemented]`) |

---

## 3. Invariants & Risk Mitigation
- **Invariants**: `app/src/main/` untouched; `app/source-reading-baseline.txt == 105`; `app/ui-reflection-baseline.txt == 189`; `4` user-owned dirty `tools/` files & `docs/presentations/vorgehensmodell/` untouched; `Localization Parity: N/A - no UI strings`.
- **Rollback**: All additions are isolated to test files and living doc status/reconciliation lines on `feature/ATT-8327`.
