# Stage 3: Implementation Plan - ATT-3704: Add dedicated Athlete Profile screen with timestamped history and time-series plots for Weight, Resting HR, FTP, and W/kg

**Ticket**: [ATT-3704](https://rainerblind.atlassian.net/browse/ATT-3704)  
**Sub-task**: `ATT-3832` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.6`  
**Requirement Mapping**: `REQ-UI-391`  
**Test Mapping**: `TST-UI-354`  
**Branch**: `feature/ATT-3704`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

Currently, **Athlete Weight**, **Resting Heart Rate (`HR_REST`)**, and **Functional Threshold Power (`FTP`)** are stored only as single overwritten scalar keys in `SettingsDataStore` (`KEY_ATHLETE_WEIGHT_KG`, `KEY_HR_REST`, `KEY_CYCLING_FTP`). There is no dedicated **Athlete Profile** screen in the Navigation Drawer, no timestamped SQLite history log, and no interactive time-series plot for Weight, Resting HR, FTP, or derived **Power-to-Weight (`W/kg`)**.

`ATT-3704` adds a dedicated **Athlete Profile** screen (`NavRoutes.ATHLETE_PROFILE`) backed by `AthleteMetrics.db`, unidirectional synchronization into `SettingsDataStore`, a 2x2 summary grid, an interactive time-series chart with `1M`/`3M`/`6M`/`1Y`/`All` time ranges, and a chronological history list with Edit (`DatePickerDialog`) and Delete (`DeleteConfirmationDialog`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-391` (*Dedicated Athlete Profile Screen, Timestamped Body Metrics SQLite History, Derived W/kg Time-Series Plot & Unidirectional SettingsDataStore Projection*)
* **Test Mapping**: `TST-UI-354` (`AthleteMetricsDtoAndMathTest`, `AthleteMetricsRepositorySyncTest`, `AthleteProfileContractAndLocalizationTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Unidirectional Data Flow (`AthleteMetricsRepository -> SettingsDataStore`)**: `AthleteMetricsRepository` is the single authority that writes timestamped entries to `AthleteMetrics.db` and projects the chronologically latest entry per metric (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`) into `SettingsDataStore`. `SettingsDataStore` never calls back into `AthleteMetricsRepository`, preventing circular dependency or feedback loops.
2. **Thread Safety & Single-Thread Database Dispatcher**: All SQLite queries and mutations in `AthleteMetricsDatabaseManager` and `AthleteMetricsRepository` execute on a dedicated `CoroutineDispatcher` (`Dispatchers.IO`) and refresh reactive `StateFlow`s atomically.
3. **Database & DTO Mapping Symmetry (Rule 20)**: `AthleteMetricEntry.fromCursor(Cursor)` and `AthleteMetricEntry.fromCursorBatch(Cursor)` use identical column indices and never close the passed `Cursor` inside the mapper.
4. **Canonical Unit & Precision Formatting (Rule 36 & `REQ-TRK-019`)**:
   - Weight is always stored in canonical `kg` (`Double`) in SQLite and converted via `SettingsDataStore.LBS_PER_KG` (`2.20462262185f`) when `TrainingApplication.getUnit() == MyUnits.IMPERIAL` (formatted `%.1f kg` / `%.1f lbs`).
   - Derived `W/kg` is computed in `Double` precision (`ftpWatts / weightKg`) and formatted to 2 decimal places (`%.2f W/kg`).
5. **Subtask Self-Sufficiency & Parent Human Gate Invariance (Rule 3 & Rule 4)**: Subtasks transition directly to `Erledigt` upon passing Gate audit; parent ticket `ATT-3704` stops at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: SQLite Persistence & DTO Mappers (`database/AthleteMetricsDatabaseManager.kt`)
* Define `enum class AthleteMetricType(val minMetricValue: Double, val maxMetricValue: Double)`:
  - `WEIGHT_KG(30.0, 300.0)`
  - `HR_REST_BPM(30.0, 120.0)`
  - `FTP_WATTS(50.0, 600.0)`
  with helper `fun isValidMetricValue(value: Double): Boolean = value.isFinite() && value in minMetricValue..maxMetricValue`.
* Define `data class AthleteMetricEntry(val id: Long = 0L, val timestampMs: Long, val metricType: AthleteMetricType, val value: Double)` with companion `fromCursor(cursor: Cursor): AthleteMetricEntry` and `fromCursorBatch(cursor: Cursor): List<AthleteMetricEntry>` (Rule 20).
* Define `AthleteMetricsDbHelper(context: Context)` (`AthleteMetrics.db`, version `1`) creating table `athlete_metrics_history` (`_id`, `timestamp_ms`, `metric_type`, `value CHECK(value > 0)`) and index `idx_athlete_metrics_type_time ON athlete_metrics_history (metric_type, timestamp_ms DESC, _id DESC)`.
* Define `AthleteMetricsDatabaseManager` singleton with CRUD methods:
  - `getAllEntries(): List<AthleteMetricEntry>` (ordered by `timestamp_ms DESC, _id DESC`)
  - `getEntriesByType(type: AthleteMetricType): List<AthleteMetricEntry>` (ordered by `timestamp_ms DESC, _id DESC`)
  - `getLatestEntry(type: AthleteMetricType): AthleteMetricEntry?`
  - `insertEntry(timestampMs: Long, type: AthleteMetricType, value: Double): Long`
  - `updateEntry(id: Long, timestampMs: Long, value: Double): Int`
  - `deleteEntry(id: Long): Int`

### Component 2: Time-Series Plot Math & Derived `W/kg` (`ui/athleteprofile/AthleteMetricsPlotMath.kt`)
* Define `enum class PlotMetricType { WEIGHT, HR_REST, FTP, POWER_TO_WEIGHT }` and `enum class MetricsTimeRange(val days: Int?) { ONE_MONTH(30), THREE_MONTHS(90), SIX_MONTHS(180), ONE_YEAR(365), ALL(null) }`.
* Define `data class MetricPlotPoint(val timestampMs: Long, val value: Double)`.
* Implement pure functions:
  - `computeDerivedPowerToWeightSeries(entries: List<AthleteMetricEntry>): List<MetricPlotPoint>`: sorts weight and FTP entries chronologically (`timestampMs ASC, id ASC`), tracks the latest active `weightKg` and `ftpWatts` at each distinct timestamp where either metric was recorded, and emits `MetricPlotPoint(t, ftpWatts / weightKg)` whenever both `weightKg > 0.0` and `ftpWatts > 0.0` are active.
  - `buildPlotSeries(entries: List<AthleteMetricEntry>, metricType: PlotMetricType, timeRange: MetricsTimeRange, nowMs: Long, isImperial: Boolean): List<MetricPlotPoint>`: filters points by cutoff (`nowMs - days * 86_400_000L`) and converts `WEIGHT` values by `SettingsDataStore.LBS_PER_KG` when `isImperial == true`.
  - Canonical formatting helpers: `formatWeight(weightKg: Double, isImperial: Boolean): String`, `formatHrRest(bpm: Double): String`, `formatFtp(watts: Double): String`, `formatPowerToWeight(wPerKg: Double): String` (`String.format(Locale.US, "%.2f", wPerKg)`).

### Component 3: Unidirectional Repository & DataStore Sync (`ui/athleteprofile/AthleteMetricsRepository.kt` & `preferences/SettingsDataStore.kt`)
* In `SettingsDataStore.kt`:
  - Add `KEY_ATHLETE_METRICS_SEEDED = booleanPreferencesKey("athlete_metrics_seeded")` (`athleteMetricsSeededFlow`, `setAthleteMetricsSeeded(Boolean)`).
  - Add `clearHrRest()` and `clearFtp()` helpers so deleting the final entry for a metric cleanly removes the scalar key from `SettingsDataStore`.
* In `AthleteMetricsRepository.kt`:
  - Expose `entriesFlow: StateFlow<List<AthleteMetricEntry>>`.
  - Implement `suspend fun ensureSeededFromDataStore(nowMs: Long = System.currentTimeMillis())`: checks `settingsDataStore.athleteMetricsSeededFlow.first()`. If `false`, runs inside a SQLite transaction to seed existing positive `athleteWeightKgFlow` (`> 0f`), `hrRestFlow` (`> 0`), and `ftpFlow` (`> 0`, using `ftpUpdatedAtFlow ?: nowMs`) if no entries exist for that metric, then sets `setAthleteMetricsSeeded(true)`.
  - Implement `suspend fun addEntry(metricType: AthleteMetricType, value: Double, timestampMs: Long = System.currentTimeMillis()): Long`, `suspend fun updateEntry(id: Long, metricType: AthleteMetricType, value: Double, timestampMs: Long)`, and `suspend fun deleteEntry(id: Long, metricType: AthleteMetricType)`.
  - After every mutation, call `syncLatestToDataStore(metricType)` which queries `dbManager.getLatestEntry(metricType)` (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`):
    - For `WEIGHT_KG`: calls `settingsDataStore.setAthleteWeightKg(latest?.value?.toFloat() ?: 0.0f)`.
    - For `HR_REST_BPM`: calls `settingsDataStore.setHrRest(latest.value.roundToInt())` or `settingsDataStore.clearHrRest()` if `null`.
    - For `FTP_WATTS`: calls `settingsDataStore.setFtp(latest.value.roundToInt(), updatedAtMillis = latest.timestampMs)` or `settingsDataStore.clearFtp()` if `null`.

### Component 4: ViewModel, Compose Screen & Entry Point Sync (`AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, Navigation & Entry Points)
* `AthleteProfileViewModel.kt`: Combines `repository.entriesFlow`, `selectedPlotMetric`, `selectedTimeRange`, and `historyFilterMetric` into `AthleteProfileUiState` (latest Weight, latest Resting HR, latest FTP, latest derived `W/kg`, active plot series, filtered history list, unit preference `isImperial`).
* `AthleteProfileScreen.kt`:
  - Header with `R.string.athlete_profile_title` and `+` `IconButton` (`Icons.Default.Add`).
  - 2x2 summary grid of `RoundedCornerShape(12.dp)` cards (Weight, Resting HR, FTP, W/kg) showing current value, unit, and locale-formatted measurement date (`DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())`). Tapping a card selects that metric in the time-series plot; tapping the `+` action opens `AthleteMetricEntryDialog`.
  - Time-series chart card with compact single-line non-wrapping metric selector pills (`Weight`, `Resting HR`, `FTP`, `W/kg`) and time-range selector pills (`1M`, `3M`, `6M`, `1Y`, `All`) (`maxLines = 1, softWrap = false`, Rule 38), plus a Compose `Canvas` time-series plot with Y-axis gridlines/labels, X-axis date ticks, connecting polyline, and data point circles (or a friendly empty state when `< 1` data point exists in range).
  - Chronological history section with Edit (`AthleteMetricEntryDialog` + Material 3 `DatePickerDialog`) and Delete (`DeleteConfirmationDialog`).
* Update `ZonesSettingsActivity.kt` (when saving FTP or `HR_REST` via `ThresholdEditDialog`) and `EditSensorFieldDialog.kt` (when saving athlete weight in `RequiredMetricInputDialog`) to also record the entry via `AthleteMetricsRepository`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `EquipmentTabsScreen` / `ZonesSettingsScreen` (`primaryContainer` header with `LayoutConstants.HEADER_TITLE_ROW_HEIGHT`, `16.dp` horizontal padding, `RoundedCornerShape(12.dp)` cards) and `ZonesSettingsActivity` compact bordered pills (Rule 38).
* **Reused components**: `DeleteConfirmationDialog`, `EmptyStatePlaceholder`, Material 3 `DatePickerDialog`, `AlertDialog`, `OutlinedTextField`.
* **Theme tokens**: shapes `RoundedCornerShape(12.dp)` for cards and `RoundedCornerShape(8.dp)` for selector pills; spacing `4.dp`, `8.dp`, `12.dp`, `16.dp`; colors `MaterialTheme.colorScheme.primary`, `primaryContainer`, `surfaceContainerLow`, `onSurfaceVariant`, `outlineVariant`.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Vector Icon, Drawer ID, and 9-Locale Strings
* Files:
  - `app/src/main/res/drawable/ic_athlete_profile.xml`
  - `app/src/main/res/values/ids.xml`
  - `app/src/main/res/values*/strings.xml` (all 9 locales)
* Changes: Add `ic_athlete_profile.xml` (curved person + pulse/scale badge per Rule 37), `<item name="drawer_athlete_profile" type="id" />`, and localized `drawer_athlete_profile` + `athlete_profile_*` strings across all 9 locales.

### Step 2: SQLite Database Manager & Plot Math
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/AthleteMetricsDatabaseManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsPlotMath.kt`
* Changes: Implement `AthleteMetricType`, `AthleteMetricEntry` (`fromCursor`, `fromCursorBatch`), `AthleteMetricsDbHelper`, `AthleteMetricsDatabaseManager`, and `AthleteMetricsPlotMath`.

### Step 3: Unidirectional Repository & SettingsDataStore Helpers
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/preferences/SettingsDataStore.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsRepository.kt`
* Changes: Add `KEY_ATHLETE_METRICS_SEEDED`, `clearHrRest()`, `clearFtp()` in `SettingsDataStore.kt` and implement `AthleteMetricsRepository` with transactional initial seeding and unidirectional DataStore projection.

### Step 4: ViewModel, Compose Screen, Navigation Wiring & Entry-Point Sync
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/sensorgrid/EditSensorFieldDialog.kt`
* Changes: Implement `AthleteProfileViewModel` and `AthleteProfileScreen`, wire `NavRoutes.ATHLETE_PROFILE` and `R.id.drawer_athlete_profile`, and hook `ZonesSettingsActivity` and `EditSensorFieldDialog` into `AthleteMetricsRepository`.

### Step 5: Unit, Repository & Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsDtoAndMathTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsRepositorySyncTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/activities/SingleActivityNavigationTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerStateTest" --tests "com.atrainingtracker.trainingtracker.activities.SingleActivityNavigationTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4 followed by full `./gradlew testDebugUnitTest` clean-room regression suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3704` allows clean revert prior to sprint branch merge.
