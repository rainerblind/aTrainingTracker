# Stage 2: Requirement & Test Specification — ATT-8330

**Ticket**: [ATT-8330](https://rainerblind.atlassian.net/browse/ATT-8330) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`  
**Scope**: Executable Verification Specification for `66` `TST-UNT-*` / `TST-UI-*` Specs (`docs/tests/tst_ui_design_system_part1.md`, `ATT-8145`)  
**Branch**: `feature/ATT-8330` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20  

---

## 1. Attributes, Gate 1 Advisories & Governance Invariants
- **Priority**: Medium | **Type**: Functional / Non-Functional | **Verification Method**: Unit / Integration / Review
- **Zero New REQ/TST IDs**: `ATT-8330` backs `47` existing `[Implemented]` TSTs (`8` Bucket A + `12` Bucket B1 + `27` Bucket B2), preserves `1` already `[Verified]` (`TST-UI-055`), and retains `18` Bucket C (`66/66` exact parity). Living docs remain at `[Implemented]` until Stage 5 post-pass promotion.
- **Localization Parity**: `N/A - no UI strings`.
- **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131.4`)**: None (`0` `app/src/main/`, hardware sensor, HAL, or external API changes).
- **Gate 1 Advisories Incorporated**:
  1. **`TST-UI-050` -> Bucket B2 (`27` B2, `12` B1)**: Verifies 3-row vs 2-row identity layout and `18.dp` LED/battery icon alignment across `EditDeviceDialog.kt`, `SensorSourceDialog.kt`, and `DeviceItem.kt` via `createComposeRule()` (`SensorIdentity` class does not exist).
  2. **Multi-TST Parent REQ Promotion Guard (Stage 5)**:
     - **MUST Hold `[Implemented]`**: `REQ-TRK-004` (`TST-UNT-003` passes, but blocked by external `[Implemented]` `TST-MAN-001`), `REQ-UI-011` (blocked by Bucket C `TST-UI-001/016/017/018` + external `TST-LOG-001/002`), `REQ-UI-101` (`TST-UI-072`), `REQ-UI-104` (`TST-UI-073`, `076`), plus all single-TST Bucket C REQs (`REQ-MAP-009`, `014`, `015`; `REQ-UI-013`, `044`, `047`, `053`, `057`, `059`, `060`, `062`).
     - **Already `[Verified]` (Preserve)**: `REQ-EXP-006`, `REQ-FIL-001`, `REQ-FIL-002`, `REQ-MAP-011`, `REQ-MAP-012` (preserve; sibling `TST-UNT-006`, `004`, `005` promote to `[Verified]`).
     - **Eligible for `[Verified]` in Stage 5 (once `ATT-8330` TST passes)**: `REQ-PER-008` (`TST-PER-009` already `[Verified]` + `TST-UNT-012`, `013`), `REQ-UI-009`/`REQ-UI-110` (`TST-INT-004`, `TST-STR-003` already `[Verified]` + `TST-UI-029`), plus all single-TST REQs for the passing Bucket A/B1/B2 specs.
- **Guardrails (`REQ-PRO-132`, `REQ-PRO-133`, `K1`/`M1`)**: `0` `app/src/main/` edits; `0` `app/source-reading-baseline.txt` (`105`) / `app/ui-reflection-baseline.txt` (`189`) edits; `0` `src/main/` file reads; `0` `Class.forName("...Kt")` stubs; `createComposeRule()` on all `27` Bucket B2 UI checks.

---

## 2. Acceptance Criteria (`Given-When-Then`) Across All `66` TSTs

### Preserved `[Verified]` (`1` TST)
| TST ID | REQ(s) | Existing Verified Suite(s) | Given-When-Then Verification Contract |
| :--- | :--- | :--- | :--- |
| `TST-UI-055` | `REQ-MAP-011`, `012` | `WorkoutRepositoryProjectionTest.kt`, `TelemetryChartStackVisibilityTest.kt` | **Given** existing verified projection and layer-visibility suites, **When** retained untouched, **Then** `[Verified]` status and code tags remain intact. |

### Bucket A — Existing Behavioral Suites Tagged & Augmented (`8` TSTs)
| TST ID | REQ(s) | Target Test Suite (`app/src/test/`) | Given-When-Then Acceptance Contract |
| :--- | :--- | :--- | :--- |
| `TST-UNT-004` | `REQ-FIL-001` | `FilterNullSafetyTest.kt` | **Given** `NumberedMovingAverageFilter` and `TimedMovingAverageFilter`, **When** streaming numerical sample sequences and null gaps, **Then** window averages match exact arithmetic mean (`Double` precision). |
| `TST-UNT-005` | `REQ-FIL-002` | `FilterInitializationAndBoundsTest.kt` | **Given** `ExponentialSmoothingFilter` with alpha `α`, **When** feeding seeded sequence `m[k]`, **Then** outputs satisfy `y[k+1] = α·m[k] + (1-α)·y[k]` and reject out-of-range alpha. |
| `TST-UNT-011` | `REQ-PRO-005` | `TrackingViewsRealSqliteMigrationTest.kt` | **Given** fresh `onCreate` and v9->v10 `onUpgrade` SQLite DBs, **When** inspecting `Views` table, **Then** `ShowElevationProfile` column exists and initializes from `ShowMap`. |
| `TST-UNT-014` | `REQ-DAT-008` | `DatabaseMigrationIndexTest.kt` | **Given** `WorkoutSummariesDbHelper.onUpgrade` inside an open SQLite transaction, **When** upgrading schemas, **Then** upgrade completes inside `SQLiteOpenHelper`'s transaction without calling `setTransactionSuccessful()`. |
| `TST-UI-041` | `REQ-UI-033`, `035`, `036` | `InteractivePeriodMapTest.kt` | **Given** Week/Month period tracks & peak markers (`START`, `END`, `ALTITUDE`, `DISTANCE`), **When** computing adaptive markers and tapping a peak point, **Then** peak markers appear with period alpha and resolve target workout peek. |
| `TST-UI-051` | `REQ-MAP-010` | `ElevationProfileOverlayTest.kt` | **Given** `ElevationProfile` rendered via `createComposeRule()`, **When** tapping the grade info icon, **Then** the grade legend toggles visibility (`assertIsDisplayed` / `assertDoesNotExist`). |
| `TST-UI-061` | `REQ-UI-056` | `ThemeColorCentralizationAuditTest.kt` | **Given** all `app/src/main/java/**/*.kt` files outside `Color.kt`, **When** `verifyZeroHexColorsOutsideColorKt` runs, **Then** zero raw `Color(0x...)` literals exist outside centralized `Color.kt`. |
| `TST-UI-108` | `REQ-UI-155` | `DeadCodeEliminationContractTest.kt` | **Given** retired legacy fragments (`CloudUploadFragment.kt`, `SearchSettingsFragment.kt`, `StravaUploadFragment.kt`) and `res/layout/*.xml`, **When** auditing classpath and layout dir, **Then** all 3 retired classes/sources are absent and exactly the 9 active XML layouts remain. |

### Bucket B1 — Net-New Deterministic Unit / DB / ViewModel / Resource Tests (`12` TSTs)
| TST ID | REQ(s) | Target Test Suite (`app/src/test/`) | Given-When-Then Acceptance Contract |
| :--- | :--- | :--- | :--- |
| `TST-UNT-003` | `REQ-TRK-004`, `REQ-SET-003` | `EquipmentDiscoveryTest.kt` | **Given** `EquipmentAndSportTypeDiscoveryManager` with active device IDs and speed samples, **When** `resolveIdentity` executes, **Then** unique sensor-linked equipment resolves exact sport ID and unlinked sets fall back to speed threshold tiers. |
| `TST-UNT-006` | `REQ-EXP-006` | `SportTypeDatabaseManagerStravaTest.kt` | **Given** Robolectric SQLite `SportTypeDatabaseManager`, **When** storing explicit `"No upload"` (`null` Strava sport name) on a custom/existing sport, **Then** persisted lookup returns `null` / `UPLOAD_TO_STRAVA=0` across reload. |
| `TST-UNT-009` | `REQ-UI-011` | `WorkoutExtremaBigSixMapperTest.kt` | **Given** a workout cursor/domain record containing full telemetry, **When** mapping workout extrema summary rows (`WorkoutData` / `ExtremaUiState`), **Then** strictly the Big 6 metrics (HR, Speed/Pace, Cadence, Power, Altitude, Temp) are represented. |
| `TST-UNT-010` | `REQ-MAP-007` | `MappablePathMemoizationTest.kt` | **Given** `MapTrack`, `MapSegment`, `MapRoute`, and `MapWorkoutCluster` (`MappablePath`), **When** accessing `latLngs` repeatedly, **Then** the projected `List<LatLng>` maintains referential identity (`===`) without re-allocating. |
| `TST-UNT-012` | `REQ-PER-008` | `WorkoutSummariesViewModelFilterTest.kt` | **Given** `WorkoutSummariesViewModel` with mixed workouts and period bounds, **When** `getFilteredWorkouts` is called with explicit `sportId`, **Then** only workouts matching both period range and `sportId` return. |
| `TST-UNT-013` | `REQ-PER-008` | `WorkoutSummariesViewModelFilterTest.kt` | **Given** `WorkoutSummariesViewModel` with mixed sub-sports under a `BSportType`, **When** `getFilteredWorkouts` is called with `sportId = null` and `bSportType = BIKE`, **Then** all workouts matching `bSportType` in period range return. |
| `TST-UI-027` | `REQ-UI-014` | `WorkoutDataMapperExtremaTest.kt` | **Given** workout distance `100.0 m` and active duration `100 s` in `WorkoutDataMapper`, **When** mapping workout summary domain state, **Then** authoritative average speed equals `1.0 m/s` (`distance / activeTimeSec`). |
| `TST-UI-028` | `REQ-UI-018` | `UiElevationTerminologyLocalizationTest.kt` | **Given** EN and DE Android string resources, **When** resolving Ascent/Descent vs Min/Max altitude labels, **Then** relative gain uses `Ascent`/`Höhenmeter` while absolute uses `Altitude`/`Höhe`. |
| `TST-UI-029` | `REQ-UI-009`, `REQ-UI-110` | `LiveWorkoutSessionTest.kt` | **Given** an active `LiveWorkoutSession` / `BANALServiceRepository` session with accumulated track points, **When** stopping and starting a new session, **Then** prior session track points, extrema, and pause timers reset cleanly (`0` leakage). |
| `TST-UI-039` | `REQ-PRO-004` | `BANALServiceRepositoryPauseIsolationTest.kt` | **Given** an active session transitioning to paused state (`isPaused = true`), **When** feeding location/distance updates during pause, **Then** active track and profile accumulators exclude paused movement. |
| `TST-UI-056` | `REQ-MAP-013` | `TrackOnMapAftermathViewModelTest.kt` | **Given** `TrackOnMapAftermathViewModel` + `MyPreferenceManager`, **When** toggling technical track visibility flags and recreating VM, **Then** selected layer flags persist across session reload. |
| `TST-UI-075` | `REQ-UI-115` | `UiSettingsTerminologyLocalizationTest.kt` | **Given** EN and DE Android string resources for Drawer items, **When** checking Display and Search titles, **Then** EN resolves `"Display"`/`"Search"` and DE resolves `"Display"`/`"Suche"` without redundant Settings/Einstellungen suffixes. |

### Bucket B2 — Net-New Robolectric `createComposeRule()` UI Behavioral Tests (`27` TSTs)
| TST ID | REQ(s) | Target Test Suite (`app/src/test/`) | Given-When-Then Acceptance Contract |
| :--- | :--- | :--- | :--- |
| `TST-UI-011` | `REQ-UI-011` | `WorkoutExtremaUiTest.kt` | **Given** `WorkoutExtrema` rendered via `createComposeRule()`, **When** unit strings are passed, **Then** rendered unit nodes appear in `[unit]` square brackets (e.g. `[km/h]`). |
| `TST-UI-012` | `REQ-UI-011` | `WorkoutExtremaUiTest.kt` | **Given** `WorkoutExtrema`, **When** inspecting `GetTextLayoutResult` for sensor name, value, and unit, **Then** sensor name & numeric value share larger `16.sp` scale while unit is micro-scaled. |
| `TST-UI-013` | `REQ-UI-011` | `WorkoutExtremaUiTest.kt` | **Given** `WorkoutExtrema` data row, **When** comparing `boundsInRoot.bottom` of sensor label, unit, and numeric cells, **Then** row elements align at the bottom edge (`<= 2px` tolerance). |
| `TST-UI-014` | `REQ-UI-011` | `WorkoutExtremaUiTest.kt` | **Given** `WorkoutExtrema` data row, **When** reading `TextLayoutResult` on unit vs label, **Then** unit `fontSize` matches micro `labelSmall` (`< 16.sp`) with reduced alpha (`TTAlpha.Medium < 1f`). |
| `TST-UI-015` | `REQ-UI-011` | `WorkoutExtremaUiTest.kt` | **Given** `WorkoutExtrema` header row, **When** composed, **Then** `"Ø"` header symbol renders (`assertIsDisplayed`) and `"average"` word is absent. |
| `TST-UI-019` | `REQ-UI-011` | `WorkoutExtremaUiTest.kt` | **Given** `WorkoutExtrema` rows for HR/Power, Speed/Cadence, and Alt/Temp, **When** inspecting `TextLayoutResult.layoutInput.style.fontWeight`, **Then** HR/Power bold Avg+Max, Speed/Cadence bold Avg, Alt/Temp bold Min+Max. |
| `TST-UI-020` | `REQ-UI-011` | `WorkoutExtremaUiTest.kt` | **Given** `WorkoutExtrema` row with `"-"` placeholder and numeric value, **When** comparing rendered text color alpha, **Then** `"-"` placeholder renders at `TTAlpha.Disabled` (`< numeric alpha`). |
| `TST-UI-021` | `REQ-UI-012` | `WorkoutDetailsUiTest.kt` | **Given** `WorkoutDetails` with ascent/descent and workout metadata, **When** composed with `createComposeRule()`, **Then** Ascent & Descent display while redundant Min/Max altitude labels do not appear. |
| `TST-UI-030` | `REQ-UI-020` | `WorkoutDetailsUiTest.kt` | **Given** `WorkoutDetails` and `SegmentDetails`, **When** composed with `createComposeRule()`, **Then** vertical Gain/Ascent metrics render consistently with `boundsInRoot` alignment across rows. |
| `TST-UI-031` | `REQ-UI-021` | `WorkoutDetailsUiTest.kt` | **Given** `WorkoutDetails` Elevation section, **When** inspecting semantic bounds, **Then** Elevation text sits cleanly inside the elevation metric row hierarchy. |
| `TST-UI-034` | `REQ-UI-024` | `RouteAndSegmentCardsUiTest.kt` | **Given** Strava-imported vs Local `MapRoute` in `RouteSummaryHeader`, **When** composed, **Then** Strava renders Strava branding (without `"Source: Strava"` text) while Local renders `"Source: Local"`. |
| `TST-UI-035` | `REQ-UI-025` | `RouteAndSegmentCardsUiTest.kt` | **Given** `RouteSummaryHeader`, **When** comparing `boundsInRoot` gap between identity row and metrics row, **Then** compact vertical separation renders cleanly without overlap. |
| `TST-UI-036` | `REQ-UI-026` | `RouteAndSegmentCardsUiTest.kt` | **Given** `SegmentList` and `SegmentDetails`, **When** rendered via `createComposeRule()`, **Then** Strava attribution branding is constrained (`~24.dp` header scale) and never duplicated per list card. |
| `TST-UI-038` | `REQ-UI-028` | `PeriodSummaryCardUiTest.kt` | **Given** `PeriodSummaryCard` with sub-sport rows and longest workout, **When** composed, **Then** hierarchical sub-sport breakdowns and highlighted Longest Workout row display. |
| `TST-UI-053` | `REQ-UI-052` | `PeriodSummaryCardUiTest.kt` | **Given** `PeriodSummaryCard` with `longestWorkout`, **When** user clicks the Longest Workout section, **Then** `onLongestWorkoutClick(workoutId)` fires with exact workout ID. |
| `TST-UI-074` | `REQ-UI-116` | `PeriodSummaryCardUiTest.kt` | **Given** a weekly `PeriodSummary` with date subtitle `"May 11 - May 17"`, **When** `PeriodSummaryCard` renders, **Then** explicit date range string displays directly below the title. |
| `TST-UI-077` | `REQ-UI-121` | `UnifiedCardSpacingUiTest.kt` | **Given** `WorkoutSummary`, `RouteItem`, and `ClusterItem` in fixed-width container (`412.dp`), **When** measuring outer card vs content `boundsInRoot`, **Then** horizontal internal inset matches `12.dp` uniformly. |
| `TST-UI-043` | `REQ-UI-041`, `043` | `DeviceManagementUiTest.kt` | **Given** `DevicesTabbedScreen` and `EditDeviceDialog`, **When** switching tabs, toggling Pair switch, and clicking a device item, **Then** callbacks fire and `EditDeviceDialog` updates name/save cleanly. |
| `TST-UI-044` | `REQ-UI-042` | `DeviceManagementUiTest.kt` | **Given** `DeviceItem` rendered via `createComposeRule()`, **When** inspecting LED availability icon vs device metadata bounds, **Then** LED indicator sits strictly left (`ledBounds.right <= textBounds.left`) with no divider clutter. |
| `TST-UI-050` | `REQ-UI-050` | `DeviceManagementUiTest.kt` | **Given** `DeviceItem`, `EditDeviceDialog`, and `SensorSourceDialog` (`DeviceIdentityBlock`) with identical sensor model, **When** rendered, **Then** 2-row/3-row identity block and `18.dp` status/battery indicators render consistently. |
| `TST-UI-046` | `REQ-UI-046` | `StatsAndSensorStatusUiTest.kt` | **Given** `StatsSummaryBlock` rendered via `createComposeRule()`, **When** comparing label/value bounds in `UsageItem` rows, **Then** horizontal/vertical stats summary alignment holds without clipping. |
| `TST-UI-048` | `REQ-UI-048` | `StatsAndSensorStatusUiTest.kt` | **Given** `SensorStatus` with active sensors, **When** rendered, **Then** only technical icons (`~22.dp` bounds) display with zero descriptive text labels. |
| `TST-UI-049` | `REQ-UI-049` | `StatsAndSensorStatusUiTest.kt` | **Given** `SensorStatus` header & `SensorSourceDialog`, **When** tapping an active/inactive sensor icon, **Then** click callback fires cleanly for launching `SensorSourceDialog`. |
| `TST-UI-059` | `REQ-UI-054` | `MetricHierarchyAndAlphaUiTest.kt` | **Given** `MetricItem` and `WorkoutExtrema` rendered via `createComposeRule()`, **When** checking `GetTextLayoutResult` text color alphas, **Then** secondary units/placeholders use reduced `TTAlpha` vs full-alpha primary values. |
| `TST-UI-060` | `REQ-UI-055` | `MetricHierarchyAndAlphaUiTest.kt` | **Given** `MetricItem` (`highlight = true`) and `WorkoutDetails`, **When** reading rendered `color` on primary metrics (Total Distance / Active Time), **Then** text renders in `MaterialTheme.colorScheme.primary`. |
| `TST-UI-066` | `REQ-UI-058` | `MapDetailLayoutUiTest.kt` | **Given** `MapDetailLayout` (`LocalInspectionMode = true`) with `showElevationProfile = false` vs `true`, **When** composed, **Then** `ElevationProfile` semantics are omitted when `false` and rendered when `true`. |
| `TST-UI-080` | `REQ-UI-127` | `DeleteOldWorkoutsUiTest.kt` | **Given** `WorkoutListActions` and `DeleteOldWorkoutsDialog` under `createComposeRule()`, **When** clicking the delete-sweep action button, **Then** `defaultDaysToKeep` (`"365"`) displays and confirm emits parsed retention days. |

### Bucket C — Non-Automatable / Device Hardware / Live GoogleMap (`18` TSTs — Retained `[Implemented]`)
| TST ID(s) | REQ(s) | Honest Retention Rationale (Zero Fabricated Evidence per `K1`/`M1`) |
| :--- | :--- | :--- |
| `TST-UI-001`, `TST-UI-016`, `TST-UI-017`, `TST-UI-018`, `TST-UI-063`, `TST-UI-071`, `TST-UI-072` | `REQ-UI-011`, `057`, `062`, `101` | Visual inspection across physical device themes, optical glyph cap-height/baseline precision, dark-mode vector clipping, removed `TrackingHeader.kt` visual hierarchy, and card surface visual neutrality during live sync. |
| `TST-UI-026` | `REQ-UI-013` | Visual verification comparing Canvas-drawn (`drawText`) `ElevationProfile` axis labels against `WorkoutExtrema` table inside live `TrackOnMapScreen`. |
| `TST-UI-045`, `TST-UI-047`, `TST-UI-057`, `TST-UI-058`, `TST-UI-067`, `TST-UI-069` | `REQ-MAP-009`, `014`, `015`; `REQ-UI-047`, `059`, `060` | Live Play Services `GoogleMap` polyline/FAB redraw, `LiveSegmentSheet` active-segment drag limit, `GoogleMap.snapshot` async sharing chooser & rapid low-memory OOM/null bitmap Toast handling. |
| `TST-UI-054`, `TST-UI-062`, `TST-UI-073`, `TST-UI-076` | `REQ-UI-044`, `053`, `104` | Live `TrackerService` hardware GPS accuracy telemetry, physical BT vs ANT+ adapter protocol menu, and live incremental Periods DB migration Bar Graph + scroll synchronization. |

---

## 3. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `TST-UNT-003..014`, `TST-UI-001..108` (`docs/tests/tst_ui_design_system_part1.md`) and linked `REQ-*` specs across `docs/requirements/`.
- **Historical Origin & Commit Trace**: Authored during early Sprint `2025/2026-40.x` UI design system build-out prior to `ATT-8145` (`REQ-PRO-099`), `ATT-8198` (`REQ-PRO-132`), and `ATT-8199` (`REQ-PRO-133`), plus `ATT-1018`/`ATT-1068` (`REQ-UI-155`).
- **Root Reason for Existing Formulation**: Legacy `MovingAverageFilter.java`, `ExponentialSmoothingFilter.java`, and `TrackingHeader.kt` were modernized/replaced in Compose/Kotlin; early mock tests (`EquipmentDiscoveryTest`, `DeleteOldWorkoutsConfirmationTest`) predated behavioral Robolectric `createComposeRule()` mandates, and multi-TST `REQ-*` blocks share external `[Implemented]` specs (`TST-MAN-001`, `TST-LOG-001/002`).
- **Preservation of Core Invariants**: Keep all `66` living doc TSTs at their current statuses until Stage 5 (`1` `[Verified]`, `65` `[Implemented]`), enforce `REQ-PRO-132` (`ui-reflection-baseline == 189`) and `REQ-PRO-133` (`source-reading-baseline == 105`), and obey multi-TST `[Verified]` gating (`REQ-TRK-004`, `REQ-UI-011`, `REQ-UI-101`, `REQ-UI-104` stay `[Implemented]`).
