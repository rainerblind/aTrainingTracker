# Stage 2: Requirement & Test Specification - ATT-8327

**Ticket**: [ATT-8327](https://rainerblind.atlassian.net/browse/ATT-8327) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Scope**: Executable Verification & Reconciliation for 48 Legacy TSTs (`docs/tests/tst_process_stability_part1.md`, `ATT-8145`)
**Branch**: `feature/ATT-8327` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. Attributes & Governance Invariants
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit / Integration / Review
- **`SYS.2` / `SYS.3` Impact**: None (`REQ-PRO-131.4`: test coverage, traceability tagging, and legacy TST reconciliation only).
- **Localization Parity**: `N/A - no UI strings`.
- **Anti-Fabrication & Guardrail Compliance (`K1`/`M1`, `REQ-PRO-132`, `REQ-PRO-133`)**:
  1. Zero fabricated manual/physical/OAuth evidence: Bucket C (`7` TSTs) remains honestly `[Implemented]`.
  2. Zero `app/src/main/` source-text reads in any new/modified `app/src/test/` test (`app/source-reading-baseline.txt == 105`, `LEGITIMATE_GOVERNANCE_GUARDS == 22`).
  3. Zero reflection-only `Class.forName("...Kt")` UI presence stubs (`app/ui-reflection-baseline.txt == 189`): all new Compose UI tests MUST use Robolectric `createComposeRule()` behavioral assertions.
  4. Symmetric `REQ <-> TST` links (`Criterion 4`) preserved; all 48 TSTs remain `[Implemented]` until verified execution in Stage 4/5.

---

## 2. Acceptance Criteria (Given-When-Then) Across All 48 TSTs
1. **Bucket A — Existing Tests Tagged & Executed (`13` TSTs)**:
   * *Given* existing tests (`BackupRestoreInteractionQueueTest`, `test_jira_epics`/`TestGateBypassPrevention`, `verify_skills` wrapped in `test_jira_accounts`, `WorkoutClustersViewModelTest`, `AccumulatorTest`/`MyAccumulatorSensorTest`, `TrackerServiceStateMachineCharacterisationTest`, `EditWorkoutViewModelTest`/`SportTypeDatabaseManagerStravaTest`, `ArchitectureConformanceContractTest`/`TrackerServiceResumeTest`, `SensorAndDeviceConcurrencyStressTest`, `TrackerServiceIntegrationTest`, `MySensorManagerPriorityTest`, `SensorSourceDialogAltitudeCalibrationTest`/`AltitudeFromPressureDeviceTest`), *When* tagged with `TST-PRO-004`, `TST-PRO-009`, `TST-PRO-017`, `TST-BUG-001`, `TST-REG-004`, `TST-REG-007`, `TST-INT-002`, `TST-INT-003`, `TST-STR-001`, `TST-STR-002`, `TST-STR-004`, `TST-STR-011`, `TST-BUG-007` and executed, *Then* all pass (`100%`) and `extract_test_code_tags()` indexes all `13`.
2. **Bucket B1 — New Offline Python Governance & Tooling Tests (`8` TSTs)**:
   * *Given* offline mocked tests in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py` (`Rule 5`, `0` live Jira/API mutations), *When* `TST-PRO-003` (`[Impl-Plan]` subtask creation), `TST-PRO-006` (dual-gate 6-stage & human-only `Erledigt`), `TST-PRO-007` (branching/commits/post-Stage-6 merge per `REQ-PRO-120`/`125`), `TST-PRO-008` (`show`/`versions`/`set-fixversion` & gate audits), `TST-PRO-010` (`submit-stage` self-sufficiency & dedup), `TST-PRO-011` (`tools/review_agent.py` stdlib/stage/context/human-gate mock), `TST-PRO-019` (`9 Core Rules` Triad parity), and `TST-PRO-020` (`AAA` pillars + Stage 1/Rule 7.5/Ceremony 2 checks) run, *Then* all `8` pass and are indexed by `extract_test_code_tags()`.
3. **Bucket B2 — New Kotlin Unit, SQLite, Bytecode & `createComposeRule()` Tests (`20` TSTs)**:
   * *Given* new/extended tests under `app/src/test/` with `0` `src/main/` reads (`REQ-PRO-133`) and `createComposeRule()` for Compose UI (`REQ-PRO-132`), *When* `TST-LOG-001`, `TST-LOG-002` (`WorkoutDataMapper` Pace min/max & Speed+Pace), `TST-BUG-002` (`PeriodSummariesDbTest` gap-month sync), `TST-BUG-008`, `TST-BUG-009`, `TST-BUG-010`, `TST-BUG-011` (`DevicesDatabaseManagerTest` + `AltitudeFromPressureDeviceTest` + `DeviceItem` icon/toggle/self-heal), `TST-STR-003` (`TrackingViewsDbHelper` `v9->v10`), `TST-STR-005` (`ATrainingTrackerMap` bytecode/DSL), `TST-STR-006`, `TST-STR-007`, `TST-STR-008` (`createComposeRule()` `MetricBadge`/`LocalMetricFormatter`/`MappableListItem`+`EmptyStatePlaceholder`), `TST-INT-004`, `TST-INT-005`, `TST-INT-006`, `TST-INT-008` (`createComposeRule()` Elevation toggle, narrow-width `TrackingTabConfigHeader`, checkbox sequence, settings routes), `TST-REG-002`, `TST-REG-005` (`createComposeRule()` `PoweredByStrava` & `MapDetailLayout` alpha `1.0`/`0.3`/`0.1`), and `TST-STR-012`, `TST-STR-017` (`AspiceSweAuditContractTest` Jira subtask automation & `docs/design_guidelines.md` §4 format-string hardening) execute via Tier 1 `--rerun`, *Then* all `20` pass alongside `SourceTextReadingAuditContractTest` & `UiReflectionOnlyTestAuditContractTest`.
4. **Bucket C — Non-Automatable / Honest `[Implemented]` Retention (`7` TSTs)**:
   * *Given* `TST-MAN-001` (23-REQ physical ad-hoc), `TST-REG-001` (10-min GPS background jitter), `TST-REG-003` & `TST-REG-008` (live Strava/Dropbox OAuth), `TST-INT-007` (visual icon untinted identity), `TST-BUG-006` (Pixel 10 status-bar framing), and `TST-STR-015` (`app/src/main` KDoc audit blocked by `REQ-PRO-133`/`extract_test_code_tags`), *When* `verify_requirement_governance.py --check-verified-evidence` runs, *Then* all `7` stay `[Implemented]` with attested rationale and `0` fabricated evidence (`exit 0`).

---

## 3. Chesterton's Fence Audit & Spec Reconciliation (`docs/tests/tst_process_stability_part1.md`)

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `TST-STR-017` (`REQ-PRO-012`), `TST-PRO-007` (`REQ-PRO-015`), `TST-PRO-008` (`REQ-PRO-014`), `TST-PRO-010` (`REQ-PRO-017`), `TST-PRO-017` (`REQ-PRO-024`), `TST-PRO-019` (`REQ-PRO-026`), `TST-PRO-020` (`REQ-PRO-027`) in `docs/tests/tst_process_stability_part1.md`.
- **Historical Origin & Commit Trace**: Authored in `ATT-422`, `ATT-523`, `ATT-670`, `ATT-945`, `ATT-1508`, `ATT-3323`, and `ATT-3534` prior to `ATT-8145` (`REQ-PRO-099`), `ATT-8164` (`REQ-PRO-120`), `ATT-8207` (`REQ-PRO-125`), and `ATT-8030` (`Rule 7.5`).
- **Root Reason for Existing Formulation**: Early specs pinned `.cursorrules`, `16 rules / 4 tiers`, `Freigabe (Human)`, `project_protocol.md` format-string prose, and Stage 5 `processDebugResources` before Document Triad streamlining (`ATT-3323`), `9 Core Rules` (`REQ-PRO-120.3`), Stage 6 (`ATT-8207`), and `Rule 7.5` (`ATT-8030`).
- **Preservation of Core Invariants**: Preserves the underlying governance contracts (Human Decision Gate `REQ-PRO-016`, `ATT-` branching & `--no-ff` sprint merges, subtask self-sufficiency, skill/template parity, Document Triad separation, format-specifier hardening in `docs/design_guidelines.md` §4 + `TranslationParityTest`, and `AAA` hypotheses/sweeps) while keeping all `48` TSTs at `[Implemented]` until Stage 5 verification and preserving symmetric `REQ <-> TST` edges (`Criterion 4`).
