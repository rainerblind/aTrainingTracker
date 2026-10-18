# Stage 1: Problem Domain & Root Cause Analysis — ATT-3921

## 1. Executive Summary & Ticket Context
* **Ticket Key**: `ATT-3921`
* **Summary**: Cockpit: Defaults for Bike Power should contain new Sensors (TSS, IF, ...)
* **Issue Type**: Improvement (`Verbesserung`)
* **Parent Epic**: `ATT-1290` (Training Theory & Physiological Performance Engine)
* **Target Requirement**: `REQ-UI-409` (Net-new requirement extending default `BIKE_POWER` cockpit layouts and live physiological metric gating)
* **Target Test ID**: `TST-UI-372`

When a cyclist rides with a power meter (`ActivityType.BIKE_POWER`), the live cockpit screens seeded on a clean install in `TrackingViewsDatabaseManager.java` currently date back to the legacy pre-`ATT-1290` sensor catalog. Specifically:
1. `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, context)` includes `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, and `TSS` (added in `ATT-1291`), but omits `SensorType.W_PRIME_BAL` (Anaerobic Battery $W'\text{bal}$, added in `ATT-3586`).
2. `TrackingViewsDatabaseManager.TrackingViewsDbHelper.onCreate(SQLiteDatabase db)` only seeds 3 default tabs (`Standard`, `Map`, `Laps`) for `BIKE_POWER`, and its default `BIKE_POWER` sensor grids (`createViewMap`, `getDefaultStartRowDataList`, `getDefaultLapRowDataList`) only place `POWER` (`NORMAL`) alongside `TORQUE`, `PEDAL_POWER_BALANCE`, `PEDAL_SMOOTHNESS_L/R`, and `TORQUE_EFFECTIVENESS_L/R`, omitting `POWER_TO_WEIGHT` (W/kg), `NORMALIZED_POWER` (NP), `INTENSITY_FACTOR` (IF), `TSS`, `WORK_KJ`, and `W_PRIME_BAL`, and lacking a dedicated **Power** (`R.string.tab_power`) cockpit tab.
3. In the live cockpit (`SensorGridScreen.kt` / `TrackingViewModel.kt`), power physiology tiles (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, and `W_PRIME_BAL`) are rendered unconditionally even when the athlete disables the corresponding physiological model in Advanced Settings (`enableCogganPowerMetrics` or `enableCriticalPowerModel` introduced in `ATT-3929`).

---

## 2. Forensic Root Cause & Codebase Gap Analysis

### 2.1 `ActivityType.java` (`case BIKE_POWER:`)
In `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java` (lines 140–172):
* `getSensorTypeArray(ActivityType.BIKE_POWER, Context)` returns a 26-element array containing `ACCUMULATED_POWER`, `ALTITUDE`, `ASCENT`, `DESCENT`, `CADENCE`, `CALORIES`, `DISTANCE_m`, `DISTANCE_m_LAP`, `HR`, `LAP_NR`, `PACE_spm`, `PEDAL_POWER_BALANCE`, `PEDAL_SMOOTHNESS_L`, `PEDAL_SMOOTHNESS_R`, `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, `TSS`, `SPEED_mps`, `TIME_ACTIVE`, `TIME_LAP`, `TIME_OF_DAY`, `TIME_TOTAL`, and `TORQUE`.
* **Gap**: `SensorType.W_PRIME_BAL` is missing from `case BIKE_POWER:`. Adding `SensorType.W_PRIME_BAL` immediately after `SensorType.TSS` ensures full parity between the `BIKE_POWER` sensor picker catalog and the physiological engine.

### 2.2 `TrackingViewsDatabaseManager.java` Default `BIKE_POWER` Cockpit Layouts & Migration Strategy
In `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`:
* **Tab 1 — Standard (`R.string.text_default`, `layoutNr = 1`)**:
  * Per `ATT-3921`, the `BIKE_POWER` **Standard** tab (`createViewMap`) must be a clean, road-focused 6-row primary cockpit (`showLapButton = false, showMap = false, showLiveSegments = true, showLiveClimbs = true, showNavigationHints = true`):
    * Row 1: `TIME_ACTIVE` (`SMALL`, col 1), `TIME_OF_DAY` (`SMALL`, col 2)
    * Row 2: `SPEED_mps` (`NORMAL`, col 1), `DISTANCE_m` (`NORMAL`, col 2)
    * Row 3: `HR` (`LARGE`, col 1), `CADENCE` (`LARGE`, col 2)
    * Row 4: `POWER` (`HUGE`, col 1)
    * Row 5: `POWER_TO_WEIGHT` (`NORMAL`, col 1), `NORMALIZED_POWER` (`NORMAL`, col 2)
    * Row 6: `ALTITUDE` (`NORMAL`, col 1), `ASCENT` (`NORMAL`, col 2)
* **Tab 2 — Dedicated Power Tab (`R.string.tab_power`, `layoutNr = 2`)**:
  * For `ActivityType.BIKE_POWER` only, `onCreate` seeds a second tab named `mContext.getString(R.string.tab_power)` (`layoutNr = 2`, `showLapButton = false, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true`) populated via `getDefaultPowerRowDataList()`:
    * Row 1: `TIME_ACTIVE` (`SMALL`, col 1), `HR` (`SMALL`, col 2), `CADENCE` (`SMALL`, col 3)
    * Row 2: `POWER` (`HUGE`, col 1)
    * Row 3: `POWER_TO_WEIGHT` (`LARGE`, col 1), `NORMALIZED_POWER` (`LARGE`, col 2)
    * Row 4: `INTENSITY_FACTOR` (`NORMAL`, col 1), `TSS` (`NORMAL`, col 2), `WORK_KJ` (`NORMAL`, col 3)
    * Row 5: `W_PRIME_BAL` (`LARGE`, col 1)
    * Row 6: `PEDAL_POWER_BALANCE` (`SMALL`, col 1), `TORQUE` (`SMALL`, col 2)
* **Tab 3 — Map (`R.string.tab_map`, `layoutNr = 3` for `BIKE_POWER`, `2` for other activity types)**:
  * `showLapButton = false, showMap = true, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true` (with `showElevationProfile = true` set automatically by `addDefaultTab` when `showMap == true`).
  * For `ActivityType.BIKE_POWER`, the compact telemetry strip above the map (`getDefaultMapRowDataList(ActivityType)`) includes `POWER` and `POWER_TO_WEIGHT` alongside core ride metrics:
    * Row 1: `TIME_ACTIVE` (`NORMAL`, col 1), `DISTANCE_m` (`NORMAL`, col 2)
    * Row 2: `SPEED_mps` (`NORMAL`, col 1), `HR` (`NORMAL`, col 2)
    * Row 3: `POWER` (`NORMAL`, col 1), `POWER_TO_WEIGHT` (`NORMAL`, col 2)
* **Tab 4 — Laps (`R.string.tab_laps`, `layoutNr = 4` for `BIKE_POWER`, `3` for other activity types)**:
  * `showLapButton = true, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = false`.
  * In `getDefaultLapRowDataList(ActivityType.BIKE_POWER)`, `POWER_TO_WEIGHT` is included alongside `POWER`:
    * Row 1: `TIME_LAP` (`LARGE`, col 1), `LAP_NR` (`SMALL`, col 2)
    * Row 2: `SPEED_mps` (`NORMAL`, col 1), `DISTANCE_m_LAP` (`NORMAL`, col 2)
    * Row 3: `HR` (`NORMAL`, col 1), `CADENCE` (`NORMAL`, col 2)
    * Row 4: `POWER` (`LARGE`, col 1), `POWER_TO_WEIGHT` (`NORMAL`, col 2)
    * Row 5 (if `havePressureSensor`): `ALTITUDE` (`NORMAL`, col 1), `ASCENT` (`NORMAL`, col 2)
* **Explicit Migration Strategy & Duplicate Prevention**:
  * **First-Run / Fresh-Install Only (`DB_VERSION = 13` Invariant)**: `TrackingViewsDbHelper.onCreate(SQLiteDatabase db)` is invoked exclusively by SQLite when creating `TrackingViews.db` for the first time (fresh install or app data reset). `DB_VERSION` remains `13` and `onUpgrade` does **not** inject or overwrite tabs into existing databases.
  * **Why Existing Users Are Not Migrated Destructively**: Existing users may have already added custom tabs at `layoutNr = 2, 3, 4...` or customized their `Standard` tab rows. Injecting a new tab during `onUpgrade` would risk `layoutNr` collisions or duplicate `Power` tabs. Existing users can already add a new tab or configure `W_PRIME_BAL`, `TSS`, `IF`, `NP`, and `W/kg` via `EditSensorFieldDialog` (enabled by Section 2.1 adding `W_PRIME_BAL` to `ActivityType.BIKE_POWER`). Because `onCreate` runs strictly once per database creation lifecycle, duplicate default tab creation is impossible.

### 2.3 Dynamic Live-Cockpit Gating, Row-Collapse UX & State Synchronization (`SensorFieldState.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`)
1. **Pure Gating Functions (`SensorFieldState.kt`)**:
   * Add `val sensorType: SensorType? = null` to `SensorFieldState` (populated from `config.sensorType` in `TrackingViewModel.kt`).
   * Provide `fun isSensorTypeVisibleForTuning(sensorType: SensorType?, tuningConfig: TuningConfig): Boolean`:
     * When `!tuningConfig.enableCogganPowerMetrics`, returns `false` for `SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, and `SensorType.TSS`.
     * When `!tuningConfig.enableCriticalPowerModel`, returns `false` for `SensorType.W_PRIME_BAL`.
     * Otherwise returns `true`.
   * Provide `fun filterVisibleFieldsForTuning(fields: List<SensorFieldState>, tuningConfig: TuningConfig, screenMode: ScreenMode): List<SensorFieldState>`:
     * When `screenMode == ScreenMode.CONFIGURATION`, returns `fields` **unfiltered** so that when a user manually edits or customizes their layout, all persisted rows/columns remain visible and editable, and the underlying SQLite `rowNr`/`colNr` coordinates are never corrupted.
     * When `screenMode == ScreenMode.TRACKING` or `screenMode == ScreenMode.PREVIEW`, returns `fields.filter { isSensorTypeVisibleForTuning(it.sensorType, tuningConfig) }`, preserving each remaining field's immutable `rowNr` and `colNr` properties without mutating database state.
2. **Row-Collapse UX & Smooth Layout Animation (`SensorGridScreen.kt`)**:
   * In `SensorGridContent`, rows are grouped via `val fieldsByRow = visibleFields.groupBy { it.rowNr }`.
   * If all fields in a row are hidden (e.g. Row 5 `W_PRIME_BAL` on the Power tab when `enableCriticalPowerModel == false`), that `rowNr` key is absent from `fieldsByRow`, collapsing the row cleanly with zero empty vertical gap.
   * If 1 or 2 fields in a multi-column row are hidden (e.g. `NORMALIZED_POWER` in Row 5 of Standard tab or Row 3 of Power tab, or `INTENSITY_FACTOR` and `TSS` in Row 4 of Power tab when `enableCogganPowerMetrics == false`), the remaining field(s) (`POWER_TO_WEIGHT` or `WORK_KJ`) remain in their `Row` with `Modifier.weight(1f)` and expand cleanly to fill the row width.
   * Apply `Modifier.animateContentSize()` to the sensor grid `Column` container in `SensorGridContent` so any layout adjustment is smoothly animated rather than jumping abruptly.
3. **Memoized State Synchronization (Zero Recomposition Loops)**:
   * In `SensorGridScreen.kt`, `tuningConfig` is collected from `tuningDataStore.tuningConfigFlow` (which only emits when preferences in `DataStore` change, never on 1 Hz sensor ticks).
   * In `SensorGridContent`, memoize `val visibleFields = remember(state.fields, tuningConfig, state.screenMode) { filterVisibleFieldsForTuning(state.fields, tuningConfig, state.screenMode) }` so filtering only recomputes when `state.fields`, `tuningConfig`, or `state.screenMode` actually changes, preventing any unnecessary recomposition loops during active ride tracking.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   * Net-new requirement `REQ-UI-409` extending `REQ-UI-200` / `REQ-UI-216` (`TrackingViewsDatabaseManager` default cockpit layouts) and `REQ-UI-403` (`ATT-3929` physiological model toggles).
2. **Historical Origin & Commit Trace**:
   * `TrackingViewsDatabaseManager.onCreate` and `createViewMap` were originally authored for basic cycling power sensors (`POWER`, `TORQUE`, `PEDAL_POWER_BALANCE`, `PEDAL_SMOOTHNESS`, `TORQUE_EFFECTIVENESS`) before `ATT-1291` introduced `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, and `TSS`, and before `ATT-3586` introduced `W_PRIME_BAL`.
   * Later, `ATT-3106` standardized the 3-tab default matrix (`Standard`, `Map`, `Laps`) across all `ActivityType`s.
3. **Root Reason for Existing Formulation**:
   * At the time `createViewMap` and `onCreate` were written, derived physiological power metrics did not exist in `SensorType`.
4. **Preservation of Core Invariants**:
   * Existing user-customized SQLite databases (`TrackingViews.db`) are **not** overwritten on app upgrade (`DB_VERSION` remains `13`), preventing `layoutNr` collisions or duplicate tab creation.
   * For `GENERIC_HR`, `RUN_SPEED_AND_CADENCE`, and `BIKE_SPEED_AND_CADENCE`, the 3 default tabs (`layoutNr` 1, 2, 3) and their toggle matrices remain 100% unchanged.
   * In `ScreenMode.CONFIGURATION`, all persisted fields remain unfiltered so manual layout editing never corrupts `rowNr`/`colNr` coordinates.

---

## 4. User Scope Grounding & Verification Plan (`ATT-1250`)
* **In Scope**:
  1. Add `SensorType.W_PRIME_BAL` to `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, context)`.
  2. Add localized string `<string name="tab_power">...</string>` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  3. Update `TrackingViewsDatabaseManager.TrackingViewsDbHelper` so `ActivityType.BIKE_POWER` seeds 4 default tabs (`Standard`, `Power`, `Map`, `Laps`) with the exact row/column/ViewSize layouts specified in `ATT-3921`, and includes `POWER` and `POWER_TO_WEIGHT` in `BIKE_POWER` Map and Laps tabs.
  4. Propagate `sensorType: SensorType?` in `SensorFieldState`, memoize `filterVisibleFieldsForTuning(state.fields, tuningConfig, state.screenMode)` with `remember(...)` and `Modifier.animateContentSize()` in `SensorGridScreen.kt`, keeping `ScreenMode.CONFIGURATION` unfiltered while gating `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS` (by `enableCogganPowerMetrics`) and `W_PRIME_BAL` (by `enableCriticalPowerModel`) in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`.
  5. Update `TrackingViewsDatabaseManagerTest.kt` and add dedicated unit tests in `BikePowerCockpitDefaultsAndGatingTest.kt`, including a custom-layout test verifying that toggling `TuningConfig` hides/shows fields in `TRACKING`/`PREVIEW` mode without corrupting `rowNr`/`colNr` mappings for remaining fields and keeps all fields visible in `CONFIGURATION` mode.
* **Out of Scope**:
  * Destructive migration of existing user-customized `TrackingViews.db` rows on upgrade.
  * Changes to non-`BIKE_POWER` default sensor layouts.
