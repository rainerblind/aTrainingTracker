# Stage 2: Requirement & Test Specification — ATT-3921

## 1. Requirement Specification (`REQ-UI-409`)

* **Requirement ID**: `REQ-UI-409`
* **Title**: Default `BIKE_POWER` Cockpit Tabs (`Standard`, `Power`, `Map`, `Laps`), `W_PRIME_BAL` Sensor Catalog Inclusion, and Live Cockpit Gating by Physiology Model Toggles.
* **Parent Ticket**: `ATT-3921` (`Cockpit: Defaults for Bike Power should contain new Sensors (TSS, IF, ...)`)
* **Target Files**:
  * `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewModel.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorGridScreen.kt`
  * `app/src/main/res/values*/strings.xml` (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`)

### 1.1 Formal Clauses
1. **`ActivityType.BIKE_POWER` Sensor Catalog (`REQ-UI-409.1`)**:
   * `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, Context)` SHALL include `SensorType.W_PRIME_BAL` immediately after `SensorType.TSS`, alongside `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, `TSS`, `PEDAL_POWER_BALANCE`, and `TORQUE`.
2. **4-Tab Default `BIKE_POWER` Cockpit Architecture & First-Run Seeding (`REQ-UI-409.2`)**:
   * On first-run database creation (`TrackingViewsDbHelper.onCreate(SQLiteDatabase db)`), `ActivityType.BIKE_POWER` SHALL seed **4 default tabs** (`DB_VERSION` remains `13` so existing user-customized `TrackingViews.db` rows are never overwritten or duplicated on upgrade):
     1. **Tab 1 — Standard (`R.string.text_default`, `layoutNr = 1`)**:
        * Toggles: `showLapButton = false, showMap = false, showLiveSegments = true, showLiveClimbs = true, showNavigationHints = true`.
        * Layout (`createViewMap` for `ActivityType.BIKE_POWER`):
          * Row 1: `TIME_ACTIVE` (`SMALL`, col 1), `TIME_OF_DAY` (`SMALL`, col 2)
          * Row 2: `SPEED_mps` (`NORMAL`, col 1), `DISTANCE_m` (`NORMAL`, col 2)
          * Row 3: `HR` (`LARGE`, col 1), `CADENCE` (`LARGE`, col 2)
          * Row 4: `POWER` (`HUGE`, col 1)
          * Row 5: `POWER_TO_WEIGHT` (`NORMAL`, col 1), `NORMALIZED_POWER` (`NORMAL`, col 2)
          * Row 6: `ALTITUDE` (`NORMAL`, col 1), `ASCENT` (`NORMAL`, col 2)
     2. **Tab 2 — Dedicated Power Tab (`R.string.tab_power`, `layoutNr = 2`)**:
        * Toggles: `showLapButton = false, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true`.
        * Layout (`getDefaultPowerRowDataList()`):
          * Row 1: `TIME_ACTIVE` (`SMALL`, col 1), `HR` (`SMALL`, col 2), `CADENCE` (`SMALL`, col 3)
          * Row 2: `POWER` (`HUGE`, col 1)
          * Row 3: `POWER_TO_WEIGHT` (`LARGE`, col 1), `NORMALIZED_POWER` (`LARGE`, col 2)
          * Row 4: `INTENSITY_FACTOR` (`NORMAL`, col 1), `TSS` (`NORMAL`, col 2), `WORK_KJ` (`NORMAL`, col 3)
          * Row 5: `W_PRIME_BAL` (`LARGE`, col 1)
          * Row 6: `PEDAL_POWER_BALANCE` (`SMALL`, col 1), `TORQUE` (`SMALL`, col 2)
     3. **Tab 3 — Map (`R.string.tab_map`, `layoutNr = 3` for `BIKE_POWER`, `2` for other activity types)**:
        * Toggles: `showLapButton = false, showMap = true, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true`.
        * Layout (`getDefaultMapRowDataList(ActivityType.BIKE_POWER)`):
          * Row 1: `TIME_ACTIVE` (`NORMAL`, col 1), `DISTANCE_m` (`NORMAL`, col 2)
          * Row 2: `SPEED_mps` (`NORMAL`, col 1), `HR` (`NORMAL`, col 2)
          * Row 3: `POWER` (`NORMAL`, col 1), `POWER_TO_WEIGHT` (`NORMAL`, col 2)
     4. **Tab 4 — Laps (`R.string.tab_laps`, `layoutNr = 4` for `BIKE_POWER`, `3` for other activity types)**:
        * Toggles: `showLapButton = true, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = false`.
        * Layout (`getDefaultLapRowDataList(ActivityType.BIKE_POWER)`):
          * Row 1: `TIME_LAP` (`LARGE`, col 1), `LAP_NR` (`SMALL`, col 2)
          * Row 2: `SPEED_mps` (`NORMAL`, col 1), `DISTANCE_m_LAP` (`NORMAL`, col 2)
          * Row 3: `HR` (`NORMAL`, col 1), `CADENCE` (`NORMAL`, col 2)
          * Row 4: `POWER` (`LARGE`, col 1), `POWER_TO_WEIGHT` (`NORMAL`, col 2)
          * Row 5 (when `havePressureSensor`): `ALTITUDE` (`NORMAL`, col 1), `ASCENT` (`NORMAL`, col 2)
3. **Live-Cockpit Gating, Empty-State Protection & Memoized State Synchronization (`REQ-UI-409.3`)**:
   * `SensorFieldState` (`SensorFieldState.kt`) SHALL include `val sensorType: SensorType? = null`, populated from `config.sensorType` in `TrackingViewModel.kt`.
   * `SensorFieldState.kt` SHALL define pure, null-safe functions:
     * `isSensorTypeVisibleForTuning(sensorType: SensorType?, tuningConfig: TuningConfig? = null): Boolean`: resolves `val effectiveConfig = tuningConfig ?: TuningConfig()` (handling initial app boot before `DataStore` initialization); returns `false` for `NORMALIZED_POWER`, `INTENSITY_FACTOR`, and `TSS` when `!effectiveConfig.enableCogganPowerMetrics`; returns `false` for `W_PRIME_BAL` when `!effectiveConfig.enableCriticalPowerModel`; returns `true` otherwise.
     * `filterVisibleFieldsForTuning(fields: List<SensorFieldState>, tuningConfig: TuningConfig? = null, screenMode: ScreenMode = ScreenMode.TRACKING): List<SensorFieldState>`: returns `fields` unfiltered when `screenMode == ScreenMode.CONFIGURATION` (so manual layout editing never hides persisted rows or corrupts `rowNr`/`colNr`), and returns `fields.filter { isSensorTypeVisibleForTuning(it.sensorType, tuningConfig) }` in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`.
   * **Row-Collapse & All-Gated Empty-State Invariant**:
     * If all `SensorFieldState` elements in an individual row are gated off while other rows remain visible, the parent container SHALL collapse that gated row's height to `0` (by omitting that `rowNr` key from `val fieldsByRow = visibleFields.groupBy { it.rowNr }`) to prevent inter-row layout gaps.
     * If **all** rows in the tab are gated off (`visibleFields.isEmpty()`), `SensorGridContent` SHALL maintain a minimum interactive container height (`defaultMinSize(minHeight = 48.dp)`) or render a safe empty-state placeholder without crashing or collapsing the screen to a zero-pixel non-interactive area.
     * Toggling `TuningConfig` is a pure read-time UI projection and SHALL NEVER trigger a SQLite database write or shift/mutate persisted `rowNr`/`colNr` coordinates.
   * `SensorGridScreen.kt` SHALL memoize `val visibleFields = remember(state.fields, tuningConfig, state.screenMode) { filterVisibleFieldsForTuning(state.fields, tuningConfig, state.screenMode) }` and SHALL ensure smooth layout transitions for row collapsing (`AnimatedVisibility` or `Modifier.animateContentSize()`) without recomposition loops.
4. **9-Language Localization Parity (`REQ-UI-409.4`)**:
   * `<string name="tab_power">...</string>` SHALL be defined across all 9 supported application locales (`values`: `"Power"`, `values-de`: `"Leistung"`, `values-es`: `"Potencia"`, `values-fr`: `"Puissance"`, `values-it`: `"Potenza"`, `values-ja`: `"パワー"`, `values-nl`: `"Vermogen"`, `values-pl`: `"Moc"`, `values-pt`: `"Potência"`).

### 1.2 Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   * Net-new requirement `REQ-UI-409`, extending `REQ-UI-200`, `REQ-UI-216` (`TrackingViewsDatabaseManager.java`), `REQ-UI-399` (`ActivityType.java` / `W_PRIME_BAL`), and `REQ-UI-403` (`ATT-3929` physiology model toggles). Targets `ActivityType.java`, `TrackingViewsDatabaseManager.java`, `SensorFieldState.kt`, `TrackingViewModel.kt`, and `SensorGridScreen.kt`.
2. **Historical Origin & Commit Trace**:
   * `ATT-1291` (`REQ-TRK-021`, Sprint 2026-42.4) introduced `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, and `TSS` into `SensorType.java` and `ActivityType.java`.
   * `ATT-3106` (`REQ-UI-216`, Sprint 2026-41.8) standardized the 3-tab default cockpit seeding (`Standard`, `Map`, `Laps`) in `TrackingViewsDatabaseManager.onCreate()`.
   * `ATT-3586` / `ATT-3862` (`REQ-UI-399`, Sprint 2026-42.7) introduced `SensorType.W_PRIME_BAL`.
   * `ATT-3929` (`REQ-UI-403`, Sprint 2026-42.8) introduced `enableCogganPowerMetrics` and `enableCriticalPowerModel` in `TuningConfig`.
3. **Root Reason for Existing Formulation**:
   * `TrackingViewsDatabaseManager.createViewMap` and `onCreate` were originally written when only hardware pedal/torque sensors (`POWER`, `TORQUE`, `PEDAL_POWER_BALANCE`, `PEDAL_SMOOTHNESS_L/R`, `TORQUE_EFFECTIVENESS_L/R`) existed for `BIKE_POWER`, before derived physiological metrics (`POWER_TO_WEIGHT`, `NP`, `IF`, `TSS`, `WORK_KJ`, `W_PRIME_BAL`) and progressive disclosure toggles existed.
4. **Preservation of Core Invariants**:
   * **Why `DB_VERSION = 13` Is Not Incremented**: Incrementing `DB_VERSION` and injecting tabs during `onUpgrade` would risk colliding with user-customized `layoutNr` indices or creating duplicate `Power` tabs for existing users who already customized their `BIKE_POWER` views. Keeping `DB_VERSION = 13` ensures `onCreate` seeds the 4-tab layout cleanly on fresh installs/resets while existing users retain their customized layouts and can pick `W_PRIME_BAL` from `ActivityType.BIKE_POWER`.
   * **Coordinate & Database Non-Mutation**: Filtering in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW` is a pure in-memory UI transformation (`filterVisibleFieldsForTuning`) that never writes to SQLite, and `ScreenMode.CONFIGURATION` remains 100% unfiltered so manual tile editing never shifts or corrupts `rowNr`/`colNr`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Given** `ActivityType.BIKE_POWER`, **When** `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)` is invoked, **Then** the returned array includes `W_PRIME_BAL` immediately after `TSS`.
* **Given** a fresh database creation (`TrackingViewsDbHelper.onCreate(db)`), **When** default tabs are seeded, **Then** `ActivityType.BIKE_POWER` seeds 4 tabs (`Standard` at `layoutNr = 1`, `Power` at `layoutNr = 2`, `Map` at `layoutNr = 3`, `Laps` at `layoutNr = 4`) with the exact sensor grids and tab toggle matrices specified in `REQ-UI-409.2`, while `GENERIC_HR`, `RUN_SPEED_AND_CADENCE`, and `BIKE_SPEED_AND_CADENCE` seed 3 tabs (`layoutNr = 1, 2, 3`).
* **Given** a cockpit tab containing a row where all `SensorFieldState` items are gated off, **When** `filterVisibleFieldsForTuning` and `visibleFields.groupBy { it.rowNr }` are evaluated in `ScreenMode.TRACKING` or `ScreenMode.PREVIEW`, **Then** the gated row is completely removed from `fieldsByRow` (collapsing its row height to `0` without inter-row gaps), remaining rows/columns preserve their exact `rowNr`/`colNr` coordinates without any SQLite write, and if all rows in the tab are gated off (`visibleFields.isEmpty()`) or `tuningConfig == null`, the UI handles the empty state safely (`defaultMinSize(minHeight = 48.dp)`) without throwing `IndexOutOfBoundsException` or `IllegalArgumentException`.

---

## 2. Test Specification (`TST-UI-372`)

* **Test ID**: `TST-UI-372`
* **Target Test Classes**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/BikePowerCockpitDefaultsAndGatingTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`

### 2.1 Test Cases
1. **`TST-UI-372.1`: `ActivityType.BIKE_POWER` Sensor Catalog Test**:
   * Verify `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)` contains `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, `TSS`, `W_PRIME_BAL`, `PEDAL_POWER_BALANCE`, and `TORQUE`.
2. **`TST-UI-372.2`: `TrackingViewsDbHelper` 4-Tab `BIKE_POWER` Default Layouts & Toggle Matrix Test**:
   * Verify `onCreate` seeds 3 tabs (`layoutNr` 1, 2, 3) for `GENERIC_HR`, `RUN_SPEED_AND_CADENCE`, and `BIKE_SPEED_AND_CADENCE`, and seeds **4 tabs** (`layoutNr` 1 = Standard, `layoutNr` 2 = Power, `layoutNr` 3 = Map, `layoutNr` 4 = Laps) for `BIKE_POWER`.
   * Verify `createViewMap` for `BIKE_POWER` produces the 6-row Standard layout (`TIME_ACTIVE`, `TIME_OF_DAY`, `SPEED_mps`, `DISTANCE_m`, `HR`, `CADENCE`, `POWER` `HUGE`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `ALTITUDE`, `ASCENT`).
   * Verify `getDefaultPowerRowDataList()` produces the 6-row Power tab layout (`TIME_ACTIVE`/`HR`/`CADENCE`, `POWER` `HUGE`, `POWER_TO_WEIGHT`/`NORMALIZED_POWER` `LARGE`, `INTENSITY_FACTOR`/`TSS`/`WORK_KJ` `NORMAL`, `W_PRIME_BAL` `LARGE`, `PEDAL_POWER_BALANCE`/`TORQUE` `SMALL`).
   * Verify `getDefaultMapRowDataList(ActivityType.BIKE_POWER)` and `getDefaultLapRowDataList(ActivityType.BIKE_POWER)` include `POWER` and `POWER_TO_WEIGHT`.
3. **`TST-UI-372.3`: Live-Cockpit Gating, Empty-Row Zero-Height Collapse & Custom-Layout Coordinate Preservation Test**:
   * Verify `isSensorTypeVisibleForTuning` and `filterVisibleFieldsForTuning` across `TuningConfig` combinations:
     * **Empty-Row Collapse Scenario (`height = 0` in `Column` layout)**: Verify that when all fields in a row are gated off (e.g., Row 5 `W_PRIME_BAL` on the Power tab when `enableCriticalPowerModel = false`, or a multi-column row of `[NORMALIZED_POWER, TSS]` when `enableCogganPowerMetrics = false`), `visibleFields.groupBy { it.rowNr }` completely removes that `rowNr` entry from the rendered `Column` rows.
     * **Partial-Row Expansion Scenario**: When `enableCogganPowerMetrics = false`, verify `NP`, `IF`, and `TSS` are removed while `POWER_TO_WEIGHT` (Row 5 on Standard, Row 3 on Power) and `WORK_KJ` (Row 4 on Power) remain as the sole cell in their respective `Row`s (`fieldsByRow[rowNr]?.size == 1`), expanding cleanly via `Modifier.weight(1f)`.
     * **Custom User Layout & `ScreenMode.CONFIGURATION` Coordinate Preservation**: Simulate a custom layout with mixed `rowNr`/`colNr` coordinates, verify that toggling `TuningConfig` in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW` hides/shows gated fields without mutating or corrupting the `rowNr`/`colNr` coordinates of remaining fields, and verify that `ScreenMode.CONFIGURATION` returns all fields 100% unfiltered.
4. **`TST-UI-372.4`: 9-Language Localization Parity Audit**:
   * Parse all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and verify `tab_power` exists and is non-blank in all 9 locales.
5. **`TST-UI-372.5`: All-Gated Empty Grid & Null `TuningConfig` Initial-Boot Fallback Test**:
   * Verify that when *all* fields in a custom tab are gated off (`enableCogganPowerMetrics = false, enableCriticalPowerModel = false` on a tab containing only `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS, W_PRIME_BAL]`), `filterVisibleFieldsForTuning` returns `emptyList()` and `fieldsByRow` evaluates to `emptyMap()` gracefully without throwing `IndexOutOfBoundsException` or `IllegalArgumentException`, while `SensorGridScreen.kt` maintains a safe non-zero minimum container height (`defaultMinSize(minHeight = 48.dp)`).
   * Verify that passing `tuningConfig = null` to `isSensorTypeVisibleForTuning(sensorType, null)` and `filterVisibleFieldsForTuning(fields, null, ScreenMode.TRACKING)` safely defaults to `TuningConfig()` (`enableCogganPowerMetrics = true, enableCriticalPowerModel = false`) during initial app boot before `DataStore` initialization.

---

## 3. Traceability Matrix

| Requirement ID | Clause | Test Case ID | Verification Artifact |
| :--- | :--- | :--- | :--- |
| `REQ-UI-409` | `REQ-UI-409.1` (`W_PRIME_BAL` in `BIKE_POWER` sensor catalog) | `TST-UI-372.1` | `BikePowerCockpitDefaultsAndGatingTest.kt` |
| `REQ-UI-409` | `REQ-UI-409.2` (4-tab `BIKE_POWER` defaults: Standard, Power, Map, Laps) | `TST-UI-372.2` | `BikePowerCockpitDefaultsAndGatingTest.kt`, `TrackingViewsDatabaseManagerTest.kt` |
| `REQ-UI-409` | `REQ-UI-409.3` (Live cockpit gating, empty-row collapse & custom-layout preservation) | `TST-UI-372.3` | `BikePowerCockpitDefaultsAndGatingTest.kt` |
| `REQ-UI-409` | `REQ-UI-409.4` (`tab_power` 9-locale parity) | `TST-UI-372.4` | `BikePowerCockpitDefaultsAndGatingTest.kt` |
| `REQ-UI-409` | `REQ-UI-409.3` (All-gated empty state & null `tuningConfig` boot fallback) | `TST-UI-372.5` | `BikePowerCockpitDefaultsAndGatingTest.kt` |
