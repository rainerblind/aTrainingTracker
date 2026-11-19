# Stage 5 Verification & Walkthrough: `ATT-8324`

**Ticket**: `ATT-8324` — `[Verbesserung] Add executable/attested verification for Aftermath Periods (14 TSTs from ATT-8145)`  
**Target Release**: `V5.0.0` | **Scope**: Executable Unit / Robolectric / Compose UI Backfill (`0` `app/src/main/` Edits)

---

## 1. Living Documentation & Status Promotions
- **Promoted to `[Verified]` (14 `TST-*` & 9 Self-Contained `REQ-*`)**:
  - `docs/tests/tst_aftermath_periods.md` (`v1.0.9`): `TST-PERF-001`..`010`, `TST-PER-009`, `TST-PER-010`, `TST-PER-012`, `TST-PER-017` (including Bounce 1/3 alignment: `TST-PERF-001` `<= 2,000` card vertex budget & `TST-PERF-006` `ProgressBarRangeInfo` semantics).
  - `docs/requirements/req_aftermath_periods.md` (`v1.0.8`): `REQ-PER-003`, `REQ-PER-004`, `REQ-PER-005`, `REQ-PER-006`, `REQ-PER-007`, `REQ-PER-009`.
  - `docs/requirements/req_maps_navigation.md` (`v1.0.6`): `REQ-MAP-017`.
  - `docs/requirements/req_ui_design_system_part1.md` (`v1.0.1`): `REQ-UI-120`, `REQ-UI-125`.
- **Retained `[Implemented]` (Multi-TST Guard)**: `REQ-PER-001` (pending `TST-BUG-002`), `REQ-DAT-003` (pending `TST-MAN-001`, `TST-BUG-002`), and `REQ-PER-008` (pending `TST-UNT-012`, `TST-UNT-013`).

---

## 2. Gate 4 Verified Test Matrix (`Rule 7.5` Reuse — `0` `app/` Diff Since `208a7aa3`)
Incremental `--rerun` execution across Stage 4 (`3fc8ccf6`, **93/93 PASS** across 12 suites) and Gate 4 Bounce 1/3 (`208a7aa3`, **15/15 PASS** across modified `PeriodsProgressAndEmptyStateUiTest` `3/3`, `PeriodVertexBudgetTest` `6/6`, and `SourceTextReadingAuditContractTest` `6/6`; `0` failures, `0` skipped):

| Suite (`app/src/test/`) | Result | Backed `TST-*` / `REQ-*` Coverage |
| :--- | :---: | :--- |
| `PeriodsRepositoryMemoryTest` | **6/6 PASS** | `TST-PERF-001` / `REQ-MAP-017` (520-workout empty `workoutIdToPathMap`, `<= 8` thumbnails) |
| `PeriodVertexBudgetTest` | **6/6 PASS** | `TST-PERF-001` / `REQ-MAP-017` (520-workout `<= 2,000` card & `<= 8,000` detail vertices) |
| `PeriodSummariesDbTest` | **6/6 PASS** | `TST-PERF-004`, `TST-PERF-005` / `REQ-PER-001`, `REQ-DAT-003`, `REQ-PER-004` (Robolectric SQLite incremental months & monotonic dual-phase sync) |
| `PeriodsViewModelTest` | **16/16 PASS** | `TST-PERF-008`, `TST-PER-010`, `TST-PER-012` / `REQ-PER-007`, `REQ-UI-120`, `REQ-PER-009` |
| `PeriodsProgressAndEmptyStateUiTest` | **3/3 PASS** | `TST-PERF-006` (`ProgressBarRangeInfo` `1.0f` vs `0.45f`), `TST-PER-010`, `TST-PER-017` (`createComposeRule()`, `isPlayServiceAvailable = false`) |
| `PeriodsNavigationFilterTest` | **1/1 PASS** | `TST-PER-009` / `REQ-PER-008` (`PeriodsFragment` -> `WorkoutSummariesListFragment` bundle args) |
| `WorkoutSummariesViewModelFilterTest` | **9/9 PASS** | `TST-PER-009` / `REQ-PER-008` (`getFilteredWorkouts(bSportType, startTimeS, endTimeS)`) |
| `WorkoutClusterRepositoryTest` | **10/10 PASS** | `TST-PERF-003`, `TST-PERF-007`, `TST-PERF-009` / `REQ-PER-003`, `REQ-PER-006` |
| `WorkoutClusterPreviewPersistenceTest` | **1/1 PASS** | `TST-PERF-010` / `REQ-PER-006` (Robolectric SQLite restart `previewPaths` persistence) |
| `WorkoutClustersViewModelTest` | **18/18 PASS** | `TST-PERF-002`, `TST-PERF-003`, `TST-PERF-007` / `REQ-PER-001`, `REQ-PER-003`, `REQ-PER-006` |
| `TranslationParityTest` | **11/11 PASS** | `TST-PER-017` / `REQ-UI-125` (`no_periods_available*` 9-locale parity) |
| `SourceTextReadingAuditContractTest` | **6/6 PASS** | `REQ-PRO-133` (`0` `src/main/` file reads across all tests) |

---

## 3. Governance, Device & Audit Checks
- **Governance & Master Index Parity**: `python3 tools/verify_requirement_governance.py --sync-indexes` and `--base-ref refs/heads/sprint/2026-11-19_02_Spät --check-verified-evidence` exited **`0`**.
- **`Rule 7.6` / `Rule 8.5` / `app/src/main/`**: Zero production code edits (`git diff` empty); no new UI assets or emulator deployment needed (`Verification Method: Unit`).
