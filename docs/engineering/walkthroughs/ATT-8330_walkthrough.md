# Stage 5: Walkthrough & Verification - ATT-8330: Add Executable Verification for UI Design System (66 TSTs from ATT-8145)

**DOC-ID**: `DOC-WLK-ATT-8330` | **Ticket**: [ATT-8330](https://rainerblind.atlassian.net/browse/ATT-8330) (`3 SP`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*Executable Test Verification & Attestation Backfill*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-20_01_Früh` | **Branch**: `feature/ATT-8330` | **Author**: `--as agent1`  
**Requirement Mapping**: `40` newly promoted `[Verified]` (`REQ-DAT-008`; `REQ-MAP-007`, `010`, `013`; `REQ-PER-008`; `REQ-PRO-004`, `005`; `REQ-SET-003`; `REQ-UI-009`, `012`, `014`, `018`, `020`, `021`, `024`, `025`, `026`, `028`, `033`, `035`, `036`, `041`, `042`, `043`, `046`, `048`, `049`, `050`, `052`, `054`, `055`, `056`, `058`, `110`, `115`, `116`, `121`, `127`, `155`, `521`); `5` preserved `[Verified]` (`REQ-EXP-006`, `REQ-FIL-001`, `REQ-FIL-002`, `REQ-MAP-011`, `REQ-MAP-012`); blocked multi-TST (`REQ-TRK-004`, `REQ-UI-011`, `REQ-UI-101`, `REQ-UI-104`) & Bucket C REQs retained `[Implemented]`  
**Test Mapping**: `47` newly promoted `[Verified]` (`TST-UNT-003..006`, `009..014`, `TST-UI-011..015`, `019..021`, `027..031`, `034..036`, `038`, `039`, `041`, `043`, `044`, `046`, `048..051`, `053`, `056`, `059..061`, `066`, `074`, `075`, `077`, `080`, `108`); `1` preserved `[Verified]` (`TST-UI-055`); `18` Bucket C retained `[Implemented]` (`TST-UI-001`, `016`, `017`, `018`, `026`, `045`, `047`, `054`, `057`, `058`, `062`, `063`, `067`, `069`, `071`, `072`, `073`, `076`)

---

## 1. Executive Summary & Verification Overview
Promoted `47/66` `ATT-8145` UI Design System test specifications in `docs/tests/tst_ui_design_system_part1.md` (`1` already `[Verified]` `TST-UI-055` preserved; `18` Bucket C specs honestly retained at `[Implemented]` per `K1`/`M1`) across `13` tagged Bucket A/B1 extensions and `13` net-new Bucket B1/B2 deterministic unit, SQLite, localization, and Robolectric Compose (`createComposeRule()`) test suites (`26` total `app/src/test/` files modified/created), with `0` `app/src/main/` production edits, `0` `app/src/main/` file reads (`REQ-PRO-133`), `0` `Class.forName("...Kt")` UI reflection stubs (`REQ-PRO-132`), and unchanged baselines (`app/source-reading-baseline.txt` = `105`, `app/ui-reflection-baseline.txt` = `189`).

Per `Rule 7.5` (`ATT-8030`), Stage 5 introduces **zero `app/` edits** (`git diff 3e20473b..HEAD -- app/` is empty) and directly reuses the independently verified Gate 4 SHA `3e20473b` (`129/129` PASS across `30` suites).

## 2. Suite-by-Suite Automated Test Evidence (Gate 4 SHA `3e20473b`)
Reused verified Stage 4 / Gate 4 (`ATT-9267`) locked incremental `:app:testDebugUnitTest --rerun --tests ...` JUnit XML execution across **2 batches covering all 30 targeted suites (`129/129` PASSED, `0` failures, `0` errors, `0` skipped)** (2-batch split avoided Robolectric `@GraphicsMode(NATIVE)` Test Executor OOM without modifying test scope):

| Batch | Test Suite (`app/src/test/`) | Status | Linked TSTs (`ATT-8330`) | Scope / Method Type |
| :---: | :--- | :---: | :--- | :--- |
| 1 | `FilterNullSafetyTest` | PASS | `TST-UNT-004` | Filter unit test (Bucket A) |
| 1 | `FilterInitializationAndBoundsTest` | PASS | `TST-UNT-005` | Filter initialization unit (Bucket A) |
| 1 | `SportTypeDatabaseManagerStravaTest` | PASS | `TST-UNT-006` | DB / SharedPreferences unit (Bucket B1) |
| 1 | `TrackingViewsRealSqliteMigrationTest` | PASS | `TST-UNT-011` | Real SQLite migration unit (Bucket A) |
| 1 | `DatabaseMigrationIndexTest` | PASS | `TST-UNT-014` | SQLite index migration unit (Bucket A) |
| 1 | `EquipmentDiscoveryTest` | PASS | `TST-UNT-003` | `EquipmentAndSportTypeDiscoveryManager` unit (Bucket B1) |
| 1 | `LiveWorkoutSessionTest` | PASS | `TST-UI-029` | Session isolation unit (Bucket B1) |
| 1 | `BANALServiceRepositoryPauseIsolationTest` | PASS | `TST-UI-039` | Repository pause isolation unit (Bucket B1) |
| 1 | `UiDesignSystemTerminologyTest` | PASS | `TST-UI-028`, `075` | 9-locale terminology unit (Bucket B1) |
| 1 | `WorkoutExtremaBigSixMapperTest` | PASS | `TST-UNT-009`, `TST-UI-027` | Cursor mapper / Big-6 unit (Bucket B1) |
| 1 | `InteractivePeriodMapTest` | PASS | `TST-UI-041` | Period map hit-testing unit (Bucket A) |
| 1 | `WorkoutSummariesViewModelFilterTest` | PASS | `TST-UNT-012`, `013` | Period/sport filter unit (Bucket B1) |
| 1 | `ElevationProfileOverlayTest` | PASS | `TST-UI-051` | Elevation overlay Compose/VM (Bucket A) |
| 1 | `MappablePathMemoizationTest` | PASS | `TST-UNT-010` | Path memoization identity unit (Bucket B1) |
| 1 | `TrackOnMapAftermathViewModelTest` | PASS | `TST-UI-056` | Aftermath map selection VM (Bucket B1) |
| 1 | `ThemeColorCentralizationAuditTest` | PASS | `TST-UI-061` | Theme color static audit (Bucket A) |
| 1 | `SourceTextReadingAuditContractTest` | PASS | `REQ-PRO-133` | Static baseline audit (`105`) |
| 1 | `UiReflectionOnlyTestAuditContractTest` | PASS | `REQ-PRO-132` | Static baseline audit (`189`) |
| 1 | `ArchitectureConformanceContractTest` | PASS | `REQ-PRO-129` | Architecture & LoC conformance |
| 1 | `DeadCodeEliminationContractTest` | PASS | `TST-UI-108` | Dead fragment/layout contract (Bucket A) |
| 2 | `DeviceManagementUiTest` | PASS | `TST-UI-043`, `044`, `050` | Robolectric `createComposeRule()` (Bucket B2) |
| 2 | `PeriodSummaryCardUiTest` | PASS | `TST-UI-038`, `053`, `074` | Robolectric `createComposeRule()` (Bucket B2) |
| 2 | `DeleteOldWorkoutsUiTest` | PASS | `TST-UI-080` | Robolectric `createComposeRule()` (Bucket B2) |
| 2 | `MapDetailLayoutElevationVisibilityUiTest` | PASS | `TST-UI-066` | Robolectric `createComposeRule()` (Bucket B2) |
| 2 | `TelemetryChartStackVisibilityTest` | PASS | `TST-UI-055` | Preserved `[Verified]` Compose UI |
| 2 | `WorkoutExtremaUiTest` | PASS | `TST-UI-011..015`, `019`, `020`, `059` | Robolectric `NATIVE` `createComposeRule()` (Bucket B2) |
| 2 | `WorkoutDetailsAndMetricUiTest` | PASS | `TST-UI-021`, `030`, `031`, `059`, `060` | Robolectric `NATIVE` `createComposeRule()` (Bucket B2) |
| 2 | `UnifiedCardSpacingUiTest` | PASS | `TST-UI-077` | Robolectric `NATIVE` `createComposeRule()` (Bucket B2) |
| 2 | `RouteAndSegmentCardsUiTest` | PASS | `TST-UI-034`, `035`, `036` | Robolectric `createComposeRule()` (Bucket B2) |
| 2 | `StatsAndSensorStatusUiTest` | PASS | `TST-UI-046`, `048`, `049`, `050` | Robolectric `createComposeRule()` (Bucket B2) |

## 3. Living Documentation, Citation & Governance Verification
1. **Living Doc Promotions (`REQ-PRO-120.2` Compliant)**:
   - `docs/tests/tst_ui_design_system_part1.md` (`v1.0.1 -> v1.0.2`): Promoted `47` `TST-*` specs to `[Verified]` with verbatim Gate 4 SHA `3e20473b` citations; preserved `TST-UI-055` (`[Verified]`) and retained `18` Bucket C specs (`TST-UI-001`, `016`, `017`, `018`, `026`, `045`, `047`, `054`, `057`, `058`, `062`, `063`, `067`, `069`, `071`, `072`, `073`, `076`) at `[Implemented]`; rebalanced `TST-UI-250..254` to `docs/tests/tst_ui_design_system_part3.md` (`v1.0.12 -> v1.0.13`) to satisfy `<= 4,000` line shard limit (`REQ-PRO-093.3`).
   - Promoted `40` unblocked `REQ-*` specs across `6` requirement shards (`req_data_migration_export_part1.md` `v1.0.2 -> v1.0.3` [1], `req_maps_navigation.md` `v1.0.6 -> v1.0.7` [3], `req_aftermath_periods.md` `v1.0.23 -> v1.0.24` [1], `req_process_stability_part1.md` `v1.0.0 -> v1.0.1` [2], `req_settings_profiles.md` `v1.0.9 -> v1.0.10` [1], `req_ui_design_system_part1.md` `v1.0.1 -> v1.0.2` [32]); kept `REQ-TRK-004` (blocked by `TST-MAN-001`), `REQ-UI-011` (`TST-UI-001/016/017/018`), `REQ-UI-101` (`TST-UI-072`), `REQ-UI-104` (`TST-UI-073/076`), and Bucket C REQs at `[Implemented]`.
   - Synchronized master tables (`docs/requirements.md`, `docs/tests.md`) via `--sync-indexes`.
2. **Programmatic Verbatim Citation & Governance Checks**: Confirmed `100%` of cited `.kt` file paths, class names, and `Class.method` strings resolve verbatim in `app/src/test/`, `extract_test_code_tags(".")` covers `48/48` (`47` newly verified + `TST-UI-055`), `verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_01_Früh` and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-8330_s5` exited `0`, and `git diff 3e20473b..HEAD -- app/` is empty.
3. **Branch Preservation (`REQ-PRO-125`)**: All changes remain cleanly on `feature/ATT-8330`; `Localization Parity: N/A` (zero production strings modified).

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `66` UI Design System TST specs (`docs/tests/tst_ui_design_system_part1.md`) and `46` directly linked `REQ-*` specifications across `7` domain shards flagged as unbacked in `ATT-8145`.
- **Historical Origin & Commit Trace**: Authored in Sprints `2026-36..41` prior to `ATT-8145` (`REQ-PRO-099`), `ATT-8198` (`REQ-PRO-132`), and `ATT-8199` (`REQ-PRO-133`), before executable test tagging and Robolectric Compose UI attestation were enforced.
- **Root Reason for Existing Formulation**: Status promotions `[Implemented] -> [Verified]` for the `47` backed TSTs and `40` unblocked REQs reflect verified Gate 4 test evidence (`3e20473b`, `129/129` PASS across `30` suites); the `18` hardware/timing/Map-gesture/manual-inspection Bucket C TSTs and blocked multi-TST REQs (`REQ-TRK-004`, `REQ-UI-011`, `REQ-UI-101`, `REQ-UI-104`) remain `[Implemented]` rather than relaxing invariants or fabricating unit proxies.
- **Preservation of Core Invariants**: `REQ-PRO-132` (`ui-reflection-baseline == 189`), `REQ-PRO-133` (`source-reading-baseline == 105`), `REQ-PRO-075` / `REQ-PRO-120.2` version/change-history governance, and bidirectional `Requirement(s):` <-> `Tests:` symmetry preserved intact with `0` production edits.
