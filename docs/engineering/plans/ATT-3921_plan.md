# Stage 3: Architecture & Implementation Plan — ATT-3921

## 1. Architectural Overview (SWE.2)

* **Ticket Key**: `ATT-3921` (`Cockpit: Defaults for Bike Power should contain new Sensors (TSS, IF, ...)`)
* **Target Requirement**: `REQ-UI-409`
* **Target Test Suite**: `TST-UI-372` (`BikePowerCockpitDefaultsAndGatingTest.kt`, `TrackingViewsDatabaseManagerTest.kt`)

### 1.1 Component Layering & Threading Boundaries

1. **Domain / Sensor Catalog Layer (`ActivityType.java`)**:
   * Pure synchronous catalog lookup on any thread (`getSensorTypeArray(ActivityType, Context)`).
   * Add `SensorType.W_PRIME_BAL` immediately after `SensorType.TSS` in `case BIKE_POWER:`.

2. **Persistence / SQLite Default Seeding & Non-Destructive Additive Migration Layer (`TrackingViewsDatabaseManager.java`)**:
   * **Database Versioning & Non-Destructive Additive Migration Strategy (`ensurePowerTabExistsForBikePower`)**:
     * Per approved Gate 2 requirement `REQ-UI-409.2` and contract test `TrackingViewsDatabaseManagerTest.testDatabaseVersionIs13()`, `DB_VERSION` remains `13` (no destructive `DROP` or `DELETE` of existing user configurations).
     * Instead of a destructive `DELETE` operation, `TrackingViewsDatabaseManager.TrackingViewsDbHelper` provides a **non-destructive additive migration helper** `public void ensurePowerTabExistsForBikePower(@NonNull SQLiteDatabase db)` (also invoked in `onUpgrade` when upgrading from `< 13` and exposed on `TrackingViewsDatabaseManager` for existing `DB_VERSION = 13` installations):
       1. Runs inside an atomic SQLite transaction (`db.beginTransaction() ... db.setTransactionSuccessful() ... db.endTransaction()`).
       2. Performs a `SELECT` query (`try (Cursor c = db.query(VIEWS_TABLE, new String[]{C_ID}, ACTIVITY_TYPE + "=? AND " + NAME + "=?", new String[]{ActivityType.BIKE_POWER.name(), powerTabName}, null, null, null))`) to check whether a `Power` tab already exists for `BIKE_POWER`.
       3. If the `Power` tab does not yet exist, it shifts subsequent `BIKE_POWER` tabs up by 1 (`UPDATE ViewsTable SET LayoutNr = LayoutNr + 1 WHERE ActivityType = 'BIKE_POWER' AND LayoutNr >= 2`) and calls `addDefaultTab(db, ActivityType.BIKE_POWER, powerTabName, 2, false, false, false, false, true, getDefaultPowerRowDataList())`.
       4. Zero existing user tabs or customized `ROWS_TABLE` entries are ever deleted or overwritten.
   * **Default Tab Seeding (`TrackingViewsDbHelper.onCreate(SQLiteDatabase db)`)**:
     * Update `createViewMap(boolean havePressureSensor)` for `ActivityType.BIKE_POWER` to the 6-row **Standard** layout (`TIME_ACTIVE`/`TIME_OF_DAY`, `SPEED_mps`/`DISTANCE_m`, `HR`/`CADENCE`, `POWER` `HUGE`, `POWER_TO_WEIGHT`/`NORMALIZED_POWER`, `ALTITUDE`/`ASCENT`).
     * Add `public static List<RowData> getDefaultPowerRowDataList()` returning the 6-row **Power** tab layout (`TIME_ACTIVE`/`HR`/`CADENCE` `SMALL`, `POWER` `HUGE`, `POWER_TO_WEIGHT`/`NORMALIZED_POWER` `LARGE`, `INTENSITY_FACTOR`/`TSS`/`WORK_KJ` `NORMAL`, `W_PRIME_BAL` `LARGE`, `PEDAL_POWER_BALANCE`/`TORQUE` `SMALL`).
     * Add `public static List<RowData> getDefaultMapRowDataList(ActivityType activityType)` (delegating to `getDefaultStartRowDataList()` and appending Row 3 `POWER` `NORMAL` col 1, `POWER_TO_WEIGHT` `NORMAL` col 2 when `activityType == ActivityType.BIKE_POWER`).
     * Update `getDefaultLapRowDataList(ActivityType activityType, boolean havePressureSensor)`: when `activityType == ActivityType.BIKE_POWER`, Row 4 contains `POWER` (`LARGE`, col 1) and `POWER_TO_WEIGHT` (`NORMAL`, col 2) (followed by Row 5 `ALTITUDE`/`ASCENT` when `havePressureSensor` is true).
     * When `activityType == ActivityType.BIKE_POWER`, `onCreate` seeds **4 tabs** in order:
       * `layoutNr = 1`: Standard (`R.string.text_default`, `false, false, true, true, true`, `viewMap.get(activityType)`)
       * `layoutNr = 2`: Power (`R.string.tab_power`, `false, false, false, false, true`, `getDefaultPowerRowDataList()`)
       * `layoutNr = 3`: Map (`R.string.tab_map`, `false, true, false, false, true`, `getDefaultMapRowDataList(activityType)`)
       * `layoutNr = 4`: Laps (`R.string.tab_laps`, `true, false, false, false, false`, `getDefaultLapRowDataList(activityType, havePressureSensor)`)
     * For all other `activityType`s, `onCreate` seeds the 3 canonical tabs (`layoutNr = 1, 2, 3`).

3. **ViewModel Non-Blocking State Synchronization & View-Only Row Re-Packing Layer (`SensorFieldState.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`)**:
   * **Non-Blocking `ViewModel` StateFlow (`TrackingViewModel.kt` & `SensorFieldState.kt`)**:
     * Add `val sensorType: SensorType? = null` to `SensorFieldState` (populated from `config.sensorType` in `TrackingViewModel.kt`).
     * Add `val tuningConfig: TuningConfig = TuningConfig()` to `TrackingUiState` (`SensorFieldState.kt`).
     * **Race-Condition & Non-Blocking Guarantee**: `TrackingUiState` initializes synchronously with `tuningConfig = TuningConfig()` (`enableCogganPowerMetrics = true, enableCriticalPowerModel = false`) so the initial SQLite database load of `SensorFieldConfig` never blocks waiting for `DataStore` I/O. In `TrackingViewModel.init`, `tuningDataStore.tuningConfigFlow.distinctUntilChanged()` is collected asynchronously in `viewModelScope.launch` and updates `_uiState.update { it.copy(tuningConfig = config) }` whenever preferences emit.
   * **View-Only Pure Gating & Contiguous Row Re-Packing (`SensorFieldState.kt`)**:
     * `isSensorTypeVisibleForTuning(sensorType: SensorType?, tuningConfig: TuningConfig? = null): Boolean`:
       * Resolves `val effectiveConfig = tuningConfig ?: TuningConfig()` (null-safe during initial app boot).
       * Returns `false` for `SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, and `SensorType.TSS` when `!effectiveConfig.enableCogganPowerMetrics`.
       * Returns `false` for `SensorType.W_PRIME_BAL` when `!effectiveConfig.enableCriticalPowerModel`.
       * Returns `true` otherwise.
     * `filterVisibleFieldsForTuning(fields: List<SensorFieldState>, tuningConfig: TuningConfig? = null, screenMode: ScreenMode = ScreenMode.TRACKING): List<SensorFieldState>`:
       * Returns `fields` unfiltered when `screenMode == ScreenMode.CONFIGURATION` (so manual layout editing never hides persisted rows or corrupts `rowNr`/`colNr`).
       * Returns `fields.filter { isSensorTypeVisibleForTuning(it.sensorType, tuningConfig) }` in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`.
     * **View-Only Immutability & Round-Trip Restoration (`packVisibleRowsForTuning`)**:
       * Define `fun packVisibleRowsForTuning(fields: List<SensorFieldState>, tuningConfig: TuningConfig? = null, screenMode: ScreenMode = ScreenMode.TRACKING): List<List<SensorFieldState>>`:
         1. Calls `val visible = filterVisibleFieldsForTuning(fields, tuningConfig, screenMode)`.
         2. Groups `visible` by `rowNr`, sorts by `rowNr` ascending, and maps each non-empty group to its columns sorted by `colNr` ascending (`visible.groupBy { it.rowNr }.toSortedMap().values.map { row -> row.sortedBy { it.colNr } }`).
         3. **Strict View-Only Invariant**: `packVisibleRowsForTuning` is a **100% view-only read projection**. It never mutates `SensorFieldState.rowNr` or `SensorFieldState.colNr` on any element and never writes to `TrackingViewsDatabaseManager`. When a user toggles `enableCogganPowerMetrics` or `enableCriticalPowerModel` OFF and then back ON, `packVisibleRowsForTuning` immediately restores the exact original `rowNr`/`colNr` layout from the untouched `state.fields` list.
   * **`SensorGridScreen.kt` Rendering & All-Gated Empty-State Protection**:
     * In `SensorGridContent`, memoize `val packedRows = remember(state.fields, effectiveTuningConfig, state.screenMode) { packVisibleRowsForTuning(state.fields, effectiveTuningConfig, state.screenMode) }`.
     * Apply `Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)` to the sensor grid `Column` container so when `packedRows.isEmpty()` (All-Gated state), the container renders a safe `48.dp` minimum height without throwing `IllegalStateException` or `LayoutException`.

---

## 2. UI Consistency & 9-Language Localization Table (Rule 23 & `docs/design_guidelines.md` Section 4–5)

* **Closest Existing Reference Screen**: `SensorGridScreen.kt` (`SensorGridContent`) and `TrackingTabsScreen.kt`.
* **Reused Components**: Existing `SensorFieldTile`, `Row(modifier = Modifier.fillMaxWidth())`, `Box(modifier = Modifier.weight(1f))`, `HorizontalDivider`, and `VerticalDivider` in `SensorGridScreen.kt`.
* **Theme Tokens**: Uses existing `CockpitThemeMode` (`backgroundColor`, `textColor`, `dividerColor`, `dividerThickness`) and `ViewSize` (`HUGE`, `LARGE`, `NORMAL`, `SMALL`).
* **9-Language Localization Matrix for `R.string.tab_power`**:

| Locale Directory | XML Entry | Character Length & Tab Width Verification |
| :--- | :--- | :--- |
| `values/strings.xml` (EN) | `<string name="tab_power">Power</string>` | 5 chars — compact single-line fit |
| `values-de/strings.xml` (DE) | `<string name="tab_power">Leistung</string>` | 8 chars — compact single-line fit |
| `values-es/strings.xml` (ES) | `<string name="tab_power">Potencia</string>` | 8 chars — compact single-line fit |
| `values-fr/strings.xml` (FR) | `<string name="tab_power">Puissance</string>` | 9 chars — compact single-line fit |
| `values-it/strings.xml` (IT) | `<string name="tab_power">Potenza</string>` | 7 chars — compact single-line fit |
| `values-ja/strings.xml` (JA) | `<string name="tab_power">パワー</string>` | 3 full-width katakana chars (~6 ASCII width) — zero wrapping in `TabRow` |
| `values-nl/strings.xml` (NL) | `<string name="tab_power">Vermogen</string>` | 8 chars — compact single-line fit |
| `values-pl/strings.xml` (PL) | `<string name="tab_power">Moc</string>` | 3 chars — compact single-line fit |
| `values-pt/strings.xml` (PT) | `<string name="tab_power">Potência</string>` | 8 chars — compact single-line fit |

---

## 3. Atomic Implementation Steps

### Step 1: 9-Locale Resource Addition (`tab_power`)
* **Target Files**: `app/src/main/res/values*/strings.xml` (all 9 locales listed in Section 2).
* **Action**: Add `<string name="tab_power">...</string>` right after `tab_laps` in all 9 locale files.

### Step 2: Add `SensorType.W_PRIME_BAL` to `ActivityType.BIKE_POWER`
* **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java`
* **Action**: Add `SensorType.W_PRIME_BAL` right after `SensorType.TSS` in `case BIKE_POWER:`.

### Step 3: Update `TrackingViewsDatabaseManager.java` Default `BIKE_POWER` Tabs & Non-Destructive `ensurePowerTabExistsForBikePower`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
* **Action**:
  * Update `createViewMap(boolean havePressureSensor)` for `ActivityType.BIKE_POWER` to the 6-row Standard layout (`REQ-UI-409.2`).
  * Add `public static List<RowData> getDefaultPowerRowDataList()` returning the 6-row Power layout (`REQ-UI-409.2`).
  * Add `public static List<RowData> getDefaultMapRowDataList(ActivityType activityType)` returning the 3-row Map header for `BIKE_POWER` (including `POWER` and `POWER_TO_WEIGHT`) and delegating to `getDefaultStartRowDataList()` for other activity types.
  * Update `getDefaultLapRowDataList(ActivityType activityType, boolean havePressureSensor)` so `BIKE_POWER` Row 4 has `POWER` (`LARGE`, col 1) and `POWER_TO_WEIGHT` (`NORMAL`, col 2).
  * Update `TrackingViewsDbHelper.onCreate(SQLiteDatabase db)` to seed 4 tabs (`Standard`, `Power`, `Map`, `Laps`) for `ActivityType.BIKE_POWER` and 3 tabs for other activity types.
  * Add non-destructive `ensurePowerTabExistsForBikePower(SQLiteDatabase db)` in `TrackingViewsDbHelper` (and delegate on `TrackingViewsDatabaseManager`) that queries `VIEWS_TABLE` via `SELECT` before shifting `LAYOUT_NR >= 2` and inserting the `Power` tab only if absent.

### Step 4: Non-Blocking ViewModel `tuningConfig` StateFlow & View-Only `packVisibleRowsForTuning`
* **Target Files**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewModel.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorGridScreen.kt`
* **Action**:
  * Add `val sensorType: SensorType? = null` to `SensorFieldState`, `val tuningConfig: TuningConfig = TuningConfig()` to `TrackingUiState`, and pure view-only functions `isSensorTypeVisibleForTuning(...)`, `filterVisibleFieldsForTuning(...)`, and `packVisibleRowsForTuning(...)` to `SensorFieldState.kt`.
  * Populate `sensorType = config.sensorType` and subscribe `tuningConfigFlow` non-blockingly into `TrackingUiState.tuningConfig` in `TrackingViewModel.kt`.
  * In `SensorGridScreen.kt`, memoize `packVisibleRowsForTuning` via `remember(state.fields, effectiveTuningConfig, state.screenMode)` and apply `Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)` to the sensor grid `Column`.

### Step 5: Unit & Contract Tests
* **Target Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/BikePowerCockpitDefaultsAndGatingTest.kt`
* **Dedicated Test Methods in `BikePowerCockpitDefaultsAndGatingTest.kt`**:
  1. `testBikePowerSensorCatalog_containsWPrimeBalAndAllPowerMetrics()` (`TST-UI-372.1`)
  2. `testBikePowerDefaultTabs_standardPowerMapAndLapsLayouts()` (`TST-UI-372.2`)
  3. `testEnsurePowerTabExistsForBikePower_selectsBeforeNonDestructiveInsert()` (Database Safety verification)
  4. `testPackVisibleRowsForTuning_isViewOnlyAndRestoresOriginalRowNrColNrOnToggleRoundTrip()` (`TST-UI-372.3` — verifies `SensorFieldState.rowNr` and `colNr` remain 100% immutable and restore on OFF -> ON toggle round-trip)
  5. `testAllGatedState_rendersEmptyPackedRowsAndPreservesMinSizeWithoutCrashing()` (`TST-UI-372.5` — verifies All-Gated empty grid and `defaultMinSize(minHeight = 48.dp)` contract without throwing exceptions, plus null `tuningConfig` boot fallback)
  6. `testTabPowerLocalizationParity_all9LocalesIncludingJapanese()` (`TST-UI-372.4` — verifies all 9 locales including `values-ja` `"パワー"`)
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.BikePowerCockpitDefaultsAndGatingTest"
  ```

---

## 4. Non-Negotiable Invariants & Rollback Safety
1. **Gate 3 Pre-Check (Rule 4)**: `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>` MUST pass with `GATE_PASSED` before any file under `app/src/...` is modified.
2. **Non-Destructive Database Safety Invariant**: Zero destructive `DELETE` operations on user-customized `BIKE_POWER` layouts; `ensurePowerTabExistsForBikePower` performs a `SELECT` existence check before non-destructively inserting the `Power` tab if missing.
3. **View-Only Immutability & All-Gated Safety Invariant**: `packVisibleRowsForTuning` is a pure view-only projection that never mutates `SensorFieldState.rowNr` or `colNr` and never writes to SQLite; when all fields are gated off (`packedRows.isEmpty()`), `SensorGridScreen` maintains `defaultMinSize(minHeight = 48.dp)` without crashing.
