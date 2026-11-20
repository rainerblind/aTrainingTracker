# Stage 5: Walkthrough & Verification - ATT-8328: Add Executable Verification for Settings & Profiles (43 TSTs from ATT-8145)

**DOC-ID**: `DOC-WLK-ATT-8328` | **Ticket**: [ATT-8328](https://rainerblind.atlassian.net/browse/ATT-8328) (`3 SP`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*Executable Test Verification & Attestation Backfill*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-20_01_Früh` | **Branch**: `feature/ATT-8328` | **Author**: `--as agent1`  
**Requirement Mapping**: `39` `REQ-SET-*` `[Verified]` (`REQ-SET-006..013`, `015..018`, `020`, `022..028`, `031..044`, `056`, `057`, `060`, `061`, `072`); `4` retained `[Implemented]` (`REQ-SET-014`, `019`, `021`, `069`)  
**Test Mapping**: `39` `TST-SET-*` `[Verified]`; `4` Bucket C retained `[Implemented]` (`TST-SET-006`, `011`, `013`, `058`)  

---

## 1. Executive Summary & Verification Overview
Converted `39/43` unbacked `ATT-8145` triage specifications in Settings & Profiles (`docs/tests/tst_settings_profiles.md`) to deterministic executable verification across `7` tagged Bucket A test suites and `11` new Bucket B1/B2 unit and Robolectric Compose (`createComposeRule()`) test suites, with `0` `app/src/main/` production edits, `0` `app/src/main/` file reads (`REQ-PRO-133`), `0` `Class.forName("...Kt")` UI reflection stubs (`REQ-PRO-132`), and `0` baseline regressions (`app/source-reading-baseline.txt`, `app/ui-reflection-baseline.txt` untouched).

Per `Rule 7.5` (`ATT-8030`), Stage 5 performs **zero `app/` edits** (`git diff 2a149eca..HEAD -- app/` is empty) and reuses the independently verified Gate 4 SHA `2a149eca` (`119/119` PASS across `22` suites). Per `K1`/`M1`, the `4` genuinely non-unit-automatable Bucket C entries (`TST-SET-006` superseded by `ATT-3452`, `TST-SET-011` live `GoogleMap` 3-point tap, `TST-SET-013` live `GoogleMap` marker drag, `TST-SET-058` `REQ-SET-069` physical status bar / `SheetValue.Expanded` boundary) remain honestly `[Implemented]` alongside `REQ-SET-014`, `019`, `021`, `069`.

## 2. Suite-by-Suite Automated Test Evidence (Gate 4 SHA `2a149eca`)
Reused verified Stage 4 / Gate 4 (`ATT-9248`) locked `:app:testDebugUnitTest --rerun` JUnit XML evidence (**22 suites, 119/119 PASSED, 0 failures, 0 errors, 0 skipped**, Tier 1 targeted scope + 4 governance audit suites):

| Test Class | Tests Passed | Linked TSTs (`ATT-8328`) | Scope / Method Type |
| :--- | :---: | :--- | :--- |
| `WorkoutClusterEngineLearningTest` | `9/9` | `TST-SET-001`, `005`, `007`, `025`, `026`, `033`, `034`, `035`, `047` | SQLite / Engine unit (Bucket B1) |
| `WorkoutClusterEngineRouteSyncTest` | `1/1` | `TST-SET-022` | SQLite Route-Cluster sync (Bucket B1) |
| `WorkoutClusterDatabaseConnectionTest` | `1/1` | `TST-SET-027` | Persistent DB handle unit (Bucket B1) |
| `EditWorkoutClusterTest` | `10/10` | `TST-SET-028` | Hit-count sync unit (Bucket A) |
| `UnifiedSpatialMarkersTest` | `1/1` | `TST-SET-031` | Spatial marker icon/tag test (Bucket B1) |
| `PathPreviewMapBoundsTest` | `8/8` | `TST-SET-032` | Bounds & preview Compose UI (Bucket A) |
| `WorkoutClustersViewModelTest` | `19/19` | `TST-SET-002`, `014`, `023` | ViewModel + `linkedRoute` Flow (Bucket A/B1) |
| `ClusterItemThumbnailComposeTest` | `5/5` | `TST-SET-004`, `026` | Robolectric `createComposeRule()` (Bucket A) |
| `WorkoutClustersSelectableMarkersTest` | `1/1` | `TST-SET-045` | SharedPreferences persistence (Bucket B1) |
| `WorkoutClusterDialogsUiTest` | `4/4` | `TST-SET-008`, `009`, `010`, `012`, `044` | Robolectric `createComposeRule()` (Bucket B2) |
| `ClusterImperialAndLocaleUiTest` | `2/2` | `TST-SET-015`, `030` | Robolectric Compose / Locale DE (Bucket B2) |
| `WorkoutClustersTabsUiTest` | `3/3` | `TST-SET-016`, `017`, `018`, `019`, `020`, `029`, `040`, `041` | Robolectric `createComposeRule()` (Bucket B2) |
| `WorkoutClusterDeleteTest` | `2/2` | `TST-SET-024` | SQLite delete + Compose menu (Bucket B2) |
| `ClusterTuningUiBehaviorTest` | `1/1` | `TST-SET-003` | Robolectric `createComposeRule()` (Bucket B2) |
| `EditWorkoutViewModelTest` | `12/12` | `TST-SET-046` | ViewModel `updateSportName` (Bucket B1) |
| `EditWorkoutNewClusterSyncTest` | `2/2` | `TST-SET-061` | SQLite + Compose prefill dialog (Bucket B1) |
| `WorkoutRepositorySaveClusteringTest` | `5/5` | `TST-SET-042` | Repository isolation unit (Bucket A) |
| `CollapsingAppBarNestedScrollConnectionTest` | `7/7` | `TST-SET-017` | NestedScrollConnection unit (Bucket A) |
| `SourceTextReadingAuditContractTest` | `6/6` | `REQ-PRO-049.3`, `REQ-PRO-133` | Static audit (`0` new `src/main` reads) |
| `UiReflectionOnlyTestAuditContractTest` | `4/4` | `REQ-PRO-132` | Static audit (`0` `Class.forName` UI stubs) |
| `ArchitectureConformanceContractTest` | `11/11` | `REQ-PRO-129` | Architecture & LoC invariants |
| `DeadCodeEliminationContractTest` | `5/5` | `REQ-PRO-102` | Dead code invariant |

## 3. Living Documentation, Citation & Governance Verification
1. **Living Doc Promotions**: Promoted `39` `TST-SET-*` specs (`docs/tests/tst_settings_profiles.md` `v1.0.8 -> v1.0.9`) and `39` `REQ-SET-*` specs (`docs/requirements/req_settings_profiles.md` `v1.0.8 -> v1.0.9`) to `[Verified]`; synchronized master tables (`docs/requirements.md`, `docs/tests.md`) via `--sync-indexes`.
2. **Programmatic Verbatim Citation Sweep**: Confirmed `100%` of cited test file names, class names, and method names resolve verbatim in `app/src/test/`, and `extract_test_code_tags(".")` indexes `39/39` automatable `TST-SET-*` IDs.
3. **Clean-Checkout Governance**: `verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_01_Früh` and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-8328_s5` both exited `0`.
4. **Branch Preservation (`REQ-PRO-125`)**: All changes remain on `feature/ATT-8328` (no premature merge onto `sprint/2026-11-20_01_Früh`); `Localization Parity: N/A` (zero UI strings modified); `git diff 2a149eca..HEAD -- app/` is empty.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-SET-006..072` & `TST-SET-001..061` (`43` `ATT-8145` unbacked entries in `docs/requirements/req_settings_profiles.md` & `docs/tests/tst_settings_profiles.md`).
- **Historical Origin & Commit Trace**: Authored in Sprint `2026-40.x` prior to `ATT-3452` (`REQ-SET-075`), `ATT-8145` (`REQ-PRO-099`), `ATT-8198` (`REQ-PRO-132`), and `ATT-8199` (`REQ-PRO-133`).
- **Root Reason for Existing Formulation**: Status promotions `[Implemented] -> [Verified]` for the `39` backed `REQ-SET-*` entries reflect verified Gate 4 test execution (`2a149eca`, `119/119` PASS); `REQ-SET-014` (`TST-SET-006`, superseded by `ATT-3452`), `019`, `021`, `069` stay `[Implemented]` without altering any production invariants.
- **Preservation of Core Invariants**: `REQ-SET-075` centralization, `REQ-PRO-132` (`ui-reflection-baseline == 189`), `REQ-PRO-133` (`source-reading-baseline == 105`), and symmetric `Requirement(s):` ↔ `Tests:` links preserved intact.
