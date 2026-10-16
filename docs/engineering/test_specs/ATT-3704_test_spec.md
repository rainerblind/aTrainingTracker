# Stage 2: Requirement & Test Specification - ATT-3704: Add dedicated Athlete Profile screen with timestamped history and time-series plots for Weight, Resting HR, FTP, and W/kg

**Ticket**: [ATT-3704](https://rainerblind.atlassian.net/browse/ATT-3704)  
**Sub-task**: `ATT-3831` (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.6`  
**Requirement Mapping**: `REQ-UI-391` (*Dedicated Athlete Profile Screen, Timestamped Body Metrics SQLite History, Derived W/kg Time-Series Plot & Unidirectional SettingsDataStore Projection*)  
**Test Spec ID**: `TST-UI-354`  
**Branch**: `feature/ATT-3704`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (`REQ-UI-391`)

### 1.1 Problem Statement & Rationale
Athletes need a dedicated **Athlete Profile / Body Metrics** (*Athleten-Profil*) screen accessible directly from the Navigation Drawer where they can log timestamped entries for **Body Weight** (`kg` / `lbs`), **Resting Heart Rate** (`bpm`), and **FTP** (`W`), inspect their temporal progression and derived **Power-to-Weight (`W/kg`)** in an interactive time-series chart, edit or delete past entries, and keep `SettingsDataStore` automatically synchronized with the chronologically latest measurement for each metric.

### 1.2 Functional & Architectural Requirements
1. **Navigation Drawer & Route Wiring (`REQ-UI-391.1`)**:
   - The system SHALL define `R.id.drawer_athlete_profile`, vector icon `R.drawable.ic_athlete_profile` (constructed from true curved geometric primitives in `res/drawable/` per Rule 37), and route `NavRoutes.ATHLETE_PROFILE = "athlete_profile"`.
   - `AppNavigationDrawer.kt` (`createDrawerGroups`) SHALL include `DrawerItemConfig(R.id.drawer_athlete_profile, R.drawable.ic_athlete_profile, R.string.drawer_athlete_profile)` in the `R.string.drawer__my_stuff` group.
   - `NavRoutes.fromDrawerItemId` and `NavRoutes.toDrawerItemId` SHALL map `R.id.drawer_athlete_profile <-> NavRoutes.ATHLETE_PROFILE`, and `ATrainingTrackerApp.kt` SHALL host `AthleteProfileScreen` at `composable(NavRoutes.ATHLETE_PROFILE)`.
2. **SQLite Persistence, Bounds Validation & Symmetric DTO Mappers (`REQ-UI-391.2` — Rule 20)**:
   - `AthleteMetricsDatabaseManager.kt` SHALL define `enum class AthleteMetricType(val minMetricValue: Double, val maxMetricValue: Double)`:
     - `WEIGHT_KG(30.0, 300.0)`
     - `HR_REST_BPM(30.0, 120.0)`
     - `FTP_WATTS(50.0, 600.0)`
   - `AthleteMetricsDbHelper` SHALL manage `AthleteMetrics.db` (`DATABASE_VERSION = 1`) with table `athlete_metrics_history` (`_id INTEGER PRIMARY KEY AUTOINCREMENT`, `timestamp_ms INTEGER NOT NULL`, `metric_type TEXT NOT NULL`, `value REAL NOT NULL CHECK(value > 0)`) and index `idx_athlete_metrics_type_time ON athlete_metrics_history (metric_type, timestamp_ms DESC, _id DESC)`.
   - `AthleteMetricEntry(val id: Long = 0L, val timestampMs: Long, val metricType: AthleteMetricType, val value: Double)` SHALL provide symmetric `fromCursor(cursor: Cursor): AthleteMetricEntry` and `fromCursorBatch(cursor: Cursor): List<AthleteMetricEntry>` mappers (Rule 20).
3. **Unidirectional Single Source of Truth, Delete Fallback State Machine & Transactional Seeding (`REQ-UI-391.3`)**:
   - `AthleteMetricsRepository` SHALL act as the single source of truth, projecting the chronologically latest entry per metric (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`) unidirectionally into `SettingsDataStore` (`SettingsDataStore` SHALL NEVER call back into `AthleteMetricsRepository`):
     - When a newer or equal-timestamp entry ($t \ge t_{\text{latest}}$) is added or edited, `SettingsDataStore` updates to that entry's value (and `timestampMs` for `KEY_CYCLING_FTP_UPDATED_AT`).
     - When a backdated entry ($t < t_{\text{latest}}$) is added or edited, `SettingsDataStore` retains the newest entry's value.
     - When the latest entry is deleted and older entries exist, `SettingsDataStore` reverts to the next most recent remaining entry (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`).
     - When the last remaining entry for a metric type is deleted, `SettingsDataStore` clears/resets that metric (`KEY_ATHLETE_WEIGHT_KG = 0.0f`, `KEY_HR_REST` removed, `KEY_CYCLING_FTP` & `KEY_CYCLING_FTP_UPDATED_AT` removed).
   - `ensureSeededFromDataStore()` SHALL execute once inside an explicit SQLite transaction (`beginTransaction` / `setTransactionSuccessful` / `endTransaction`) guarded by `KEY_ATHLETE_METRICS_SEEDED`, seeding existing positive `athleteWeightKgFlow`, `hrRestFlow`, and `ftpFlow` (using `ftpUpdatedAtFlow ?: nowMs`) when the history table has no entries for that metric.
   - Updating FTP or Resting HR via `ZonesSettingsActivity` or updating Weight via `EditSensorFieldDialog` SHALL record a timestamped entry through `AthleteMetricsRepository`.
4. **Derived `W/kg` Precision & Time-Series Plot Math (`REQ-UI-391.4`)**:
   - `AthleteMetricsPlotMath` SHALL filter series by `MetricsTimeRange` (`ONE_MONTH(30)`, `THREE_MONTHS(90)`, `SIX_MONTHS(180)`, `ONE_YEAR(365)`, `ALL(null)`).
   - Derived `W/kg` (`PlotMetricType.POWER_TO_WEIGHT`) SHALL be computed in `Double` precision at each distinct measurement timestamp $t$ from the effective active FTP ($F(t)$) and effective active Weight ($W(t)$) at time $t$ as $\text{wPerKg}(t) = F(t) / W(t)$, and formatted to **2 decimal places** (`%.2f W/kg`) per `REQ-TRK-019`.
   - Weight display values and Y-axis chart labels SHALL convert via `SettingsDataStore.LBS_PER_KG` (`2.20462262185f`) and format to **1 decimal place** (`%.1f kg` / `%.1f lbs`) when `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.
5. **Dedicated `AthleteProfileScreen` UI, Locale Date Formatting & 9-Language Parity (`REQ-UI-391.5` — Rule 23, Rule 38)**:
   - `AthleteProfileScreen` SHALL render:
     - Standard `primaryContainer` header with `LayoutConstants.HEADER_TITLE_ROW_HEIGHT`, screen title `R.string.athlete_profile_title`, and `+` (`Icons.Default.Add`) `IconButton`.
     - A 2x2 summary grid of `RoundedCornerShape(12.dp)` cards for **Weight**, **Resting HR**, **FTP**, and **Power-to-Weight (`W/kg`)** displaying the current value and locale-formatted latest measurement date (`DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())`).
     - An interactive time-series chart card with single-line non-wrapping metric selector pills (`Weight`, `Resting HR`, `FTP`, `W/kg`) and time-range pills (`1M`, `3M`, `6M`, `1Y`, `All`) (`maxLines = 1, softWrap = false` per Rule 38) and a Compose `Canvas` plot.
     - A chronological history log with Edit (`AthleteMetricEntryDialog` + Material 3 `DatePickerDialog` with locale-formatted date) and Delete (`DeleteConfirmationDialog`).
   - All new string resources SHALL be localized across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) using positional format specifiers (`%1$s`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Direct Navigation & Quick Entry with DatePicker)**:
  * *Given* the user selects *Athlete Profile* (`R.id.drawer_athlete_profile`) in the Navigation Drawer,
  * *When* the user adds a Weight, Resting HR, or FTP entry (for Today or a backdated date via `DatePickerDialog`),
  * *Then* the entry is persisted in SQLite, appears in the history log and time-series plot, and updates `SettingsDataStore` if and only if it is the chronologically latest entry for that metric.
* **Criterion 2 (Derived `W/kg` & Time-Series Plotting)**:
  * *Given* timestamped history entries exist for Weight, Resting HR, and/or FTP,
  * *When* evaluating `AthleteMetricsPlotMath` across metrics (`WEIGHT`, `HR_REST`, `FTP`, `POWER_TO_WEIGHT`) and time ranges (`1M`, `3M`, `6M`, `1Y`, `ALL`),
  * *Then* the series filters accurately by cutoff timestamp, converts weight for imperial units (`LBS_PER_KG`), and computes derived `W/kg` (`F(t) / W(t)`) to `2` decimal places (`%.2f`).
* **Criterion 3 (Edit & Delete Fallback State Machine)**:
  * *Given* multiple historical entries exist for a metric and the user edits or deletes the latest entry,
  * *When* the mutation completes,
  * *Then* `SettingsDataStore` automatically reverts to the next most recent remaining entry (or resets to default/null when the last entry is deleted).
* **Criterion 4 (Zone Calculator & Sensor Field Dialog Sync + Initial Seeding)**:
  * *Given* existing baselines in `SettingsDataStore` on upgrade or a baseline update saved via `ZonesSettingsActivity` / `EditSensorFieldDialog`,
  * *When* `ensureSeededFromDataStore()` or the dialog save executes,
  * *Then* corresponding timestamped entries are persisted in `athlete_metrics_history`.

### 1.4 System Invariants
- Unidirectional data flow (`AthleteMetricsRepository -> SettingsDataStore`); zero circular dependencies.
- Symmetric `fromCursor` and `fromCursorBatch` DTO mapping (Rule 20).
- Single-line non-wrapping selector pills (`maxLines = 1, softWrap = false`, Rule 38).
- 100% pass rate across `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (`TST-UI-354`)

### Test Case 1: `AthleteMetricsDtoAndMathTest` (`TST-UI-354.1`)
* **Scope**: Unit Test (DTO Symmetry, Derived `W/kg` Math, Time-Range Filtering, Imperial Conversion, Bounds Validation)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsDtoAndMathTest.kt`
* **Action & Expected Result**:
  - Verify `AthleteMetricEntry.fromCursor` and `AthleteMetricEntry.fromCursorBatch` produce identical objects on mock cursors without closing the cursor (Rule 20).
  - Verify `AthleteMetricsPlotMath.computeDerivedPowerToWeightSeries` computes `F(t) / W(t)` accurately at each timestamp and formats `W/kg` to 2 decimal places (`%.2f`).
  - Verify `AthleteMetricsPlotMath.filterAndConvertSeries` filters points by `1M`, `3M`, `6M`, `1Y`, `ALL` and converts `WEIGHT` by `SettingsDataStore.LBS_PER_KG` when `isImperial = true`.
  - Verify `AthleteMetricType` bounds validation for `WEIGHT_KG` (`30..300 kg`), `HR_REST_BPM` (`30..120 bpm`), and `FTP_WATTS` (`50..600 W`).

### Test Case 2: `AthleteMetricsRepositorySyncTest` (`TST-UI-354.2`)
* **Scope**: Integration / Repository Unit Test (Initial Seeding, Add/Edit/Delete State Machine & DataStore Projection)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsRepositorySyncTest.kt`
* **Action & Expected Result**:
  - Verify `ensureSeededFromDataStore()` seeds initial `WEIGHT_KG`, `HR_REST_BPM`, and `FTP_WATTS` (preserving `ftpUpdatedAt`) once and is idempotent on subsequent calls.
  - Verify adding a newer entry updates `SettingsDataStore`, while adding a backdated entry preserves the newer value in `SettingsDataStore`.
  - Verify deleting the newest entry reverts `SettingsDataStore` to the next most recent remaining entry, and deleting the final remaining entry resets `SettingsDataStore` (`0.0f` / `null`).

### Test Case 3: Navigation, Source Contract & 9-Language Localization Audit (`TST-UI-354.3`)
* **Scope**: Contract & Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
* **Action & Expected Result**:
  - Verify `NavRoutes.fromDrawerItemId(R.id.drawer_athlete_profile) == NavRoutes.ATHLETE_PROFILE` and `NavRoutes.toDrawerItemId(NavRoutes.ATHLETE_PROFILE) == R.id.drawer_athlete_profile`.
  - Verify `createDrawerGroups` includes `R.id.drawer_athlete_profile` in `R.string.drawer__my_stuff`.
  - Verify `ic_athlete_profile.xml` exists in `app/src/main/res/drawable/` and uses curved SVG commands (`C`/`A`, Rule 37).
  - Verify all new `athlete_profile_*` and `drawer_athlete_profile` strings exist across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with matching positional format specifiers.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-354.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the entire unit test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-354.1` | Unit | `AthleteMetricsDtoAndMathTest` | `REQ-UI-391.2`, `REQ-UI-391.4` | Specified |
| `TST-UI-354.2` | Repository | `AthleteMetricsRepositorySyncTest` | `REQ-UI-391.3` | Specified |
| `TST-UI-354.3` | Contract & Localization | `AthleteProfileContractAndLocalizationTest` | `REQ-UI-391.1`, `REQ-UI-391.5`, `REQ-UI-106` | Specified |
| `TST-UI-354.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
