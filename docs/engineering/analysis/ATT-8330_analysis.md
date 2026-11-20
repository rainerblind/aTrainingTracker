# Stage 1 Analysis: ATT-8330 — UI Design System 66-TST Verification Triage

**Ticket**: [ATT-8330](https://rainerblind.atlassian.net/browse/ATT-8330) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8330` (off `d0088018`) | **Author**: Lane A Flash (`agent1`)  

---

## 1. Problem Statement & Scope Triage (`66` TSTs)
Under `M1` (`ATT-8145`), `66` `TST-UNT-*`/`TST-UI-*` specs mapped to `docs/tests/tst_ui_design_system_part1.md` lacked machine-verified code tags or attested manual evidence. Independent codebase inspection reveals: **`1` TST is already `[Verified]`**, **`8` belong in Bucket A** (existing behavioral suites needing `// TST-*` tags), **`13` belong in Bucket B1** (automatable pure-JVM/Robolectric/SQLite/resource tests), **`26` belong in Bucket B2** (automatable Robolectric `createComposeRule()` UI behavioral tests), and **`18` belong in Bucket C** (genuinely physical/device/live-`GoogleMap`/visual-theme manual checks retained `[Implemented]`).

## 2. Independent Peer Review & Verification
* **Live Audit Corrections (against `ATT-8145` CSV)**:
  * `TST-UI-055` (`REQ-MAP-011`/`012`): Already `[Verified]` & backed by `WorkoutRepositoryProjectionTest.kt` + `TelemetryChartStackVisibilityTest.kt`; preserve untouched (`1` TST).
  * `TST-UNT-003`: Existing `EquipmentDiscoveryTest.kt` only asserts a `mockk` stub (`every { manager.resolveSportType(...) } returns 101L`); moved from naive Bucket A to **Bucket B1** to test real `EquipmentAndSportTypeDiscoveryManager` logic.
  * `TST-UI-080`: Existing `DeleteOldWorkoutsConfirmationTest.kt` only tests mocked `Context` + local lambdas; assigned to **Bucket B2** (`createComposeRule()` on `WorkoutListActions` + `DeleteOldWorkoutsDialog`).
  * `TST-UI-071`: `TrackingHeader.kt` was superseded/removed from `app/src/main/` during Compose header modernization; retained in **Bucket C** (`[Implemented]`).
  * `TST-UI-108` (`REQ-UI-155`): `CloudUploadFragment.kt`, `SearchSettingsFragment.kt`, `StravaUploadFragment.kt` are absent and `app/src/main/res/layout/` contains the exact `9` active layouts (`DeadCodeEliminationContractTest.kt` + `StarredSegmentsFragment.kt` audit -> **Bucket A**).
* **Governance Guards**: Zero `app/src/main/` edits, zero `app/source-reading-baseline.txt` / `app/ui-reflection-baseline.txt` edits, zero `src/main/` reads (`REQ-PRO-133`), zero `Class.forName("...Kt")` stubs (`REQ-PRO-132`).

### Complete 66-TST Classification Ledger (`1 + 8 + 13 + 26 + 18 = 66`)
| Bucket | Count | Exact `TST-*` IDs | Target Suites / Rationale |
|---|---:|---|---|
| **Already `[Verified]`** | **1** | `TST-UI-055` | `WorkoutRepositoryProjectionTest.kt`, `TelemetryChartStackVisibilityTest.kt` (preserve `[Verified]`) |
| **Bucket A** (Existing Suites + Tags) | **8** | `TST-UNT-004`, `TST-UNT-005`, `TST-UNT-011`, `TST-UNT-014`, `TST-UI-041`, `TST-UI-051`, `TST-UI-061`, `TST-UI-108` | `FilterNullSafetyTest.kt`, `FilterInitializationAndBoundsTest.kt`, `TrackingViewsRealSqliteMigrationTest.kt` (`v9->v10`/`onCreate` `ShowElevationProfile`), `DatabaseMigrationIndexTest.kt`, `InteractivePeriodMapTest.kt`, `ElevationProfileOverlayTest.kt`, `ThemeColorCentralizationAuditTest.kt` (`0` `Color(0x)` outside `Color.kt`), `DeadCodeEliminationContractTest.kt` (`9` active layouts + removed legacy fragments) |
| **Bucket B1** (New Unit / DB / VM / Resource) | **13** | `TST-UNT-003`, `TST-UNT-006`, `TST-UNT-009`, `TST-UNT-010`, `TST-UNT-012`, `TST-UNT-013`, `TST-UI-027`, `TST-UI-028`, `TST-UI-029`, `TST-UI-039`, `TST-UI-050`, `TST-UI-056`, `TST-UI-075` | `EquipmentAndSportTypeDiscoveryManager` real resolution (`UNT-003`); `SportTypeDatabaseManager` persistent `null` Strava state (`UNT-006`); `WorkoutDataMapper` "Big 6" extrema & avg-speed `dist/time` (`UNT-009`, `UI-027`); `MappablePath.latLngs` memoization (`UNT-010`); `WorkoutSummariesViewModel` sub-sport/base-sport filters (`UNT-012/013`); `BANALServiceRepository`/`LiveWorkoutSession` start/stop & pause isolation (`UI-029`, `039`); `SensorIdentity` model parity (`UI-050`); `MyPreferenceManager` technical tracks (`UI-056`); EN/DE Elevation/Altitude & Drawer strings (`UI-028`, `075`) |
| **Bucket B2** (`createComposeRule()` UI) | **26** | `TST-UI-011`, `012`, `013`, `014`, `015`, `019`, `020`, `021`, `030`, `031`, `034`, `035`, `036`, `038`, `043`, `044`, `046`, `048`, `049`, `053`, `059`, `060`, `066`, `074`, `077`, `080` | `WorkoutExtrema` brackets/hierarchy/bottom-bounds/fontSize/`Ø`/bolding/`"-"` alpha (`UI-011..015`, `019`, `020`); `WorkoutDetails` & `SegmentDetails` & `MetricItem` (`UI-021`, `030`, `031`, `059`, `060`); `RouteSummaryHeader`/`SegmentList` (`UI-034..036`); `PeriodSummaryCard` (`UI-038`, `053`, `074`); card `12.dp` spacing (`UI-077`); `DevicesTabbedScreen`/`DeviceItem`/`EditDeviceDialog` (`UI-043`, `044`); `StatsSummaryBlock` (`UI-046`); `SensorStatus` icons & dialog trigger (`UI-048`, `049`); `MapDetailLayout` (`UI-066`); `WorkoutListActions` + `DeleteOldWorkoutsDialog` (`UI-080`) |
| **Bucket C** (Manual / Hardware / Live Map -> Retain `[Impl]`) | **18** | `TST-UI-001`, `016`, `017`, `018`, `026`, `045`, `047`, `054`, `057`, `058`, `062`, `063`, `067`, `069`, `071`, `072`, `073`, `076` | Multi-device theme & optical/Compose-Preview glyph-baseline precision (`001`, `016`, `017`, `018`, `063`, `071`, `072`); Canvas `ElevationProfile` axis vs Extrema (`026`); live Play Services `GoogleMap` / `snapshot` / FAB / swipe (`045`, `047`, `057`, `058`, `067`, `069`); live `TrackerService` GPS provider telemetry (`054`); hardware BT/ANT+ adapter menu (`062`); live Periods incremental migration scroll/graph sync (`073`, `076`) |

---

## 3. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `TST-UNT-003..014`, `TST-UI-001..108` (`docs/tests/tst_ui_design_system_part1.md`) & linked `REQ-UI-*`, `REQ-MAP-*`, `REQ-PER-*`, `REQ-PRO-*`, `REQ-TRK-*`, `REQ-FIL-*`, `REQ-DAT-*`, `REQ-EXP-*` specs.
* **Historical Origin & Commit Trace**: Legacy Sprint `2025/2026-40.x` UI modernization specs written before `ATT-8145` (`REQ-PRO-099`), `ATT-8198` (`REQ-PRO-132`), and `ATT-8199` (`REQ-PRO-133`), plus `ATT-1018`/`ATT-1068` (`REQ-UI-155` legacy fragment/layout cleanup).
* **Root Reason for Existing Formulation**: `TrackingHeader.kt` (`REQ-UI-062`), `MovingAverageFilter.java`, and `ExponentialSmoothingFilter.java` were refactored into Jetpack Compose/Kotlin equivalents, while legacy UI tests (`EquipmentDiscoveryTest`, `DeleteOldWorkoutsConfirmationTest`) stopped at mocks instead of real unit/`createComposeRule()` checks. Multi-TST `REQ-*` guards apply to `REQ-PER-008` (also `TST-PER-009`), `REQ-TRK-004` (`TST-MAN-001`), `REQ-UI-009`/`110` (`TST-INT-004`, `TST-STR-003`), and `REQ-UI-011` (blocked by Bucket C `001/016/017/018` + external `TST-LOG-001/002`).
* **Preservation of Core Invariants**: Preserve `REQ-PRO-132` (`ui-reflection-baseline == 189`), `REQ-PRO-133` (`source-reading-baseline == 105`), multi-TST `[Verified]` gating (`REQ-*` stays `[Implemented]` unless all linked TSTs are `[Verified]`), and `K1`/`M1` zero-fabrication.

---

## 4. Change Impact Analysis
* **Affected Requirements**: `47` automatable `TST-*` headings (`8` Bucket A + `13` B1 + `26` B2) become verification-eligible in Stage 5 (`1` `TST-UI-055` already `[Verified]`); `18` Bucket C TSTs + blocked multi-TST `REQ-*` headings stay `[Implemented]`. **`SYS.2`/`SYS.3` Impact (`REQ-PRO-131.4`)**: **None** (`0` production/HAL/sensor API edits).
* **Affected Architecture Elements**: `app/src/test/` unit, SQLite, resource, and Robolectric `createComposeRule()` suites only; `0` files modified in `app/src/main/`.
* **Verification Impact & Test Surface**: Locked incremental `./gradlew :app:testDebugUnitTest --rerun --tests ...` + `SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest`, and `verify_requirement_governance.py --check-verified-evidence`. `Localization Parity: N/A - no UI strings`.
* **Risk Assessment & Invariants**: **LOW** (`3 SP`, test-only scope). High-noise Compose/Map/Device-adapter interactions explicitly isolated via `createComposeRule()` semantics and honest Bucket C boundaries.
