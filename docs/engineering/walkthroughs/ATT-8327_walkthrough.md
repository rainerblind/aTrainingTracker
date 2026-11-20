# Stage 5: Walkthrough & Verification - ATT-8327

**Ticket**: [ATT-8327](https://rainerblind.atlassian.net/browse/ATT-8327) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-8327` (Gate 4 Code SHA `d266d87b`; Bounce 1 Doc-Fix SHA `d7cf10e2`) | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20
**Living Spec Updated**: `docs/tests/tst_process_stability_part1.md` (`v1.0.1` -> `v1.0.3`: `41` TSTs `[Verified]`, `7` Bucket C TSTs honestly retained `[Implemented]`; Stage 6 Bounce 1/3 doc-only citation/history fix applied)

---

## 1. Verification Summary (`41` Verified + `7` Honestly Retained `[Implemented]`)
1. **Bucket A — Existing Suites Tagged & Enhanced (`13` TSTs -> `[Verified]`)**:
   - Python (`2`): `TST-PRO-009` (`tools/test_jira_epics.py::TestTransitionIssueEpicProtection` + `test_tst_pro_006_008_009_and_017_workflow_skills_and_human_gate`), `TST-PRO-017` (`tools/test_jira_accounts.py::TestProcessStabilityLegacyTsts::test_tst_pro_006_008_009_and_017_workflow_skills_and_human_gate`).
   - Kotlin (`11`): `TST-PRO-004` (`BackupRestoreInteractionQueueTest`), `TST-BUG-001` (`WorkoutClustersViewModelTest::selectCluster_withOver30Workouts_loadsDetailMapAndCancelsSupersededSelection_TST_PERF_002`), `TST-REG-004` (`AccumulatorTest::testPauseDiscardLogic`), `TST-REG-007` (`TrackerServiceStateMachineCharacterisationTest::testSampleAndWriteToDb_paused_updatesTotalTimeAndSuppressesSteadyStateBroadcast_TST_STB_020_1`), `TST-INT-002` (`EditWorkoutViewModelTest::testOnSportTypeSelected_noUploadStravaMapping_setsUploadToStravaZero_TST_INT_002` + `SportTypeSwimTemplateTest::testStravaSportTypeMapping_noUploadAndGravelRideRoundTrip_TST_INT_002_TST_INT_003`), `TST-INT-003` (`SportTypeSwimTemplateTest`), `TST-STR-001` (`ArchitectureConformanceContractTest`), `TST-STR-002` & `TST-STR-011` (`SensorAndDeviceConcurrencyStressTest`), `TST-STR-004` (`TrackerServiceIntegrationTest`), `TST-BUG-007` (`AltitudeFromPressureDeviceTest`).
2. **Bucket B1 — Offline Python Governance & Tooling Tests (`8` TSTs -> `[Verified]`)**:
   - `TestProcessStabilityLegacyTsts` (`tools/test_jira_accounts.py`): `test_tst_pro_003_and_010_impl_plan_subtask_and_discovery_first` (`TST-PRO-003`, `TST-PRO-010`), `test_tst_pro_006_008_009_and_017_workflow_skills_and_human_gate` (`TST-PRO-006`, `TST-PRO-008`), `test_tst_pro_007_011_019_020_governance_triad_and_review_agent_shim` (`TST-PRO-007`, `TST-PRO-011`, `TST-PRO-019`, `TST-PRO-020`).
3. **Bucket B2 — New Kotlin Unit, SQLite, Map DSL & Behavioral Compose UI Tests (`20` TSTs -> `[Verified]`)**:
   - Domain, DB & Contracts (`12`): `TST-LOG-001`, `TST-LOG-002` (`WorkoutDataMappingSymmetryTest`), `TST-BUG-002` (`PeriodSummariesDbTest`), `TST-BUG-008`–`011` (`PressureSensorRegistrationAndIconTest` real SQLite self-healing, pairing lifecycle, `R.drawable.ic_altitude`), `TST-STR-003` (`TrackingViewsRealSqliteMigrationTest` `onCreate` & `v9->v10`), `TST-STR-005` (`ProcessStabilityComposeUiTest` `MapContentScopeImpl` & `resolveMapProperties`), `TST-STR-012`, `TST-STR-017` (`AspiceSweAuditContractTest`).
   - Robolectric `createComposeRule()` UI (`8` + `MapStyle`): `TST-STR-006` (`MetricBadge`), `TST-STR-007` (`LocalMetricFormatter`), `TST-STR-008` (`MappableListItem` + `EmptyStatePlaceholder`), `TST-INT-004` (Elevation Profile toggle & chips), `TST-INT-005` (`320dp` `TrackingTabConfigHeader` fit/clip/overlap), `TST-INT-006` (Header L->R & Cockpit toggle top->bottom sequences), `TST-INT-008` (`SettingsBottomSheetType` & `NavRoutes` parity), `TST-REG-002` (`PoweredByStrava`), `TST-REG-005` (`MapStyle` `0.3f` / `0.1f` / `TTAlpha.Disabled` `0.38f`).
4. **Bucket C — Honestly Retained `[Implemented]` (`7` TSTs, `0` Fabricated Evidence per `K1`/`M1`)**:
   - `TST-MAN-001` (23-REQ physical phone tour), `TST-REG-001` (10-min GPS background jitter), `TST-REG-003` & `TST-REG-008` (live Strava/Dropbox OAuth), `TST-INT-007` (visual untinted icon identity), `TST-BUG-006` (Pixel 10 physical status-bar framing), `TST-STR-015` (`app/src/main` KDoc audit blocked by `REQ-PRO-133`/`extract_test_code_tags()`).

---

## 2. Verification Matrix (Reused Gate 4 Evidence per Rule 7.5 — Zero Code Changes Since `d266d87b`)

| Check / Suite Scope | Execution Target & Scope | Result | Status |
| :--- | :--- | :--- | :--- |
| Python Governance & Jira Suites | `python3 -m unittest tools/test_jira_accounts.py tools/test_jira_epics.py` + `tools/test_verify_requirement_governance.py -k "tst_pro"` (`offline mocked`) | **PASS (`126/126` + `30/30` = `156/156` OK)** | `Verified` |
| Targeted Kotlin Unit, SQLite & Compose UI (`Tier 1`) | `parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --rerun` across all `16` touched/new suites (`BackupRestoreInteractionQueueTest` .. `ProcessStabilityComposeUiTest`) + `3` contract guardrails (`SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest`, `DeadCodeEliminationContractTest`) | **PASS (`151/151`, `0` fail, `0` err, `0` skip)** | `Verified` |
| Governance & Static Tag Indexing (`clean-checkout` safe) | `python3 tools/verify_requirement_governance.py --repo-root . --sync-indexes` + `--base-ref refs/heads/sprint/2026-11-19_03_Nacht --check-verified-evidence` + `extract_test_code_tags()` | **PASS (`exit 0`, `41/41` indexed)** | `Verified` |

---

## 3. On-Device, Localization & Invariants
* **`Rule 7.5` / `Rule 8.5` / `Localization`**: Doc-only Stage 5 (`git diff d266d87b -- app/ tools/` empty); `0` `app/src/main/` edits; `Localization Parity: N/A - no UI strings`.
* **`REQ-PRO-132` & `REQ-PRO-133`**: `app/source-reading-baseline.txt` (`109` lines / `105` entries) and `app/ui-reflection-baseline.txt` (`192` lines / `189` entries) untouched; all new Compose UI checks use real `createComposeRule()`.
* **Branch & Tree Hygiene**: `feature/ATT-8327` preserved intact for Stage 6 Cold Review; `4` user-owned dirty `tools/` files & `docs/presentations/vorgehensmodell/` untouched.
