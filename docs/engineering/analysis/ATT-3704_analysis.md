# Stage 1 Analysis: ATT-3704 - Add dedicated Athlete Profile screen with timestamped history and time-series plots for Weight, Resting HR, FTP, and W/kg

**Ticket**: [ATT-3704](https://rainerblind.atlassian.net/browse/ATT-3704)  
**Sub-task**: `ATT-3830` (`[Analysis]`)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.6`  
**Branch**: `feature/ATT-3704`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

Currently, athlete physiological baselines and body composition metrics — **Body Weight** (`KEY_ATHLETE_WEIGHT_KG`), **Resting Heart Rate** (`KEY_HR_REST`), and **Functional Threshold Power / FTP** (`KEY_CYCLING_FTP`) — are persisted solely as single scalar values in `SettingsDataStore` (with only `KEY_CYCLING_FTP_UPDATED_AT` tracking the last modification timestamp of FTP).

This creates three user-facing limitations:
1. **Loss of Historical Progression**: Overwriting Weight, Resting HR, or FTP in `SettingsDataStore` destroys prior values, preventing athletes from inspecting how their body weight, resting heart rate, FTP, and derived power-to-weight ratio (`W/kg`) evolve across training blocks and seasons.
2. **No Dedicated Entry Point for Body Metrics**: Following `ATT-3777` (`REQ-UI-385`), the Athlete Weight row was removed from the `ZonesSettingsActivity` header to declutter zone configuration. Athletes need a first-class **Athlete Profile / Body Metrics** (*Athleten-Profil / Körperdaten*) screen directly accessible from the Navigation Drawer under *Equipment / My Stuff* (`R.string.drawer__my_stuff`).
3. **No Backdated Entry or Typo Correction**: Athletes cannot log a past measurement with a date picker, nor edit/delete a mistyped historical entry while automatically keeping `SettingsDataStore` synchronized with the chronologically latest measurement.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Current Persistence Architecture (`SettingsDataStore.kt`)
- `SettingsDataStore` (`app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStore.kt`) stores:
  - `KEY_ATHLETE_WEIGHT_KG` (`Float`, default `0.0f`, bounds `30.0..300.0 kg` / `66.0..660.0 lbs`, `LBS_PER_KG = 2.20462262185f`)
  - `KEY_HR_REST` (`Int`, bounds `30..120 bpm`)
  - `KEY_CYCLING_FTP` (`Int`, bounds `50..600 W`) and `KEY_CYCLING_FTP_UPDATED_AT` (`Long`)
- Live tracking calculations (`BANALService`, `CyclingPowerMetricsDevice`, `ZonesSettingsActivity`, `EditSensorFieldDialog`) subscribe to `athleteWeightKgFlow`, `hrRestFlow`, `ftpFlow`, and `ftpUpdatedAtFlow`.
- There is currently no SQLite table or repository storing historical time-series records for these metrics.

### 2.2 Navigation & Drawer Architecture (`NavRoutes.kt`, `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt`)
- `AppNavigationDrawer.kt` defines 6 `DrawerGroup`s (`drawer__training`, `drawer__maps`, `drawer__my_stuff`, `prefsOnlineCommunities`, `drawer__settings`, `drawer__expert_settings`).
- The `drawer__my_stuff` group (`AppNavigationDrawer.kt:174-183`) currently contains `drawer_my_sensors`, `drawer_bikes`, `drawer_shoes`, `drawer_sport_types`, and `drawer_training_zones`.
- `NavRoutes.kt` maps drawer resource IDs (`R.id.drawer_*`) bidirectionally to Compose route strings (`fromDrawerItemId` and `toDrawerItemId`), and `ATrainingTrackerApp.kt` hosts all top-level `composable(...)` destinations inside `NavHost`.

### 2.3 Synchronization Touchpoints (`ZonesSettingsActivity.kt` & `EditSensorFieldDialog.kt`)
- In `ZonesSettingsActivity.kt:384-415`, saving calculated Power zones calls `dataStore.saveCalculatedPowerZones(ftp, cutoffs)`, and saving calculated Heart Rate zones calls `dataStore.saveCalculatedHeartRateZones(zType, hrMax, hrRest, method, cutoffs)`.
- In `EditSensorFieldDialog.kt:160-164`, saving athlete weight calls `dataStore.setAthleteWeightKg(newWeight)`.
- To ensure bidirectional synchronization without breaking pure-JVM unit tests that instantiate `SettingsDataStore(mockContext, testDataStore)` with a mocked `Context`, `AthleteMetricsRepository` must:
  1. Update `SettingsDataStore` whenever an entry is added, edited, or deleted in the repository (setting the scalar preference to the chronologically latest entry for that metric, or clearing it if no entries remain).
  2. Record a timestamped entry whenever `ZonesSettingsActivity` or `EditSensorFieldDialog` saves a new FTP, Resting HR, or Weight baseline.
  3. Perform a one-time initial seed (`ensureSeededFromDataStore`) importing any existing positive `athleteWeightKgFlow`, `hrRestFlow`, and `ftpFlow` values from `SettingsDataStore` into SQLite when the history table is first initialized.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Navigation Drawer & Route Wiring**: Add `R.id.drawer_athlete_profile` under `R.string.drawer__my_stuff` in `AppNavigationDrawer.kt`, `NavRoutes.ATHLETE_PROFILE = "athlete_profile"` in `NavRoutes.kt`, and `composable(NavRoutes.ATHLETE_PROFILE)` in `ATrainingTrackerApp.kt`.
  2. **SQLite Persistence & Symmetric DTO Mapping (Rule 20)**: Create `AthleteMetricsDatabaseManager.kt` (`AthleteMetricsDbHelper`, `AthleteMetricType` enum `WEIGHT_KG`, `HR_REST_BPM`, `FTP_WATTS`, `AthleteMetricEntry` with symmetric `fromCursor` and `fromCursorBatch` mappers) and `AthleteMetricsRepository.kt`.
  3. **One-Time Initial Seeding & Bidirectional Sync**: Seed initial history entries from existing `SettingsDataStore` baselines (`athleteWeightKgFlow`, `hrRestFlow`, `ftpFlow` + `ftpUpdatedAtFlow`), update `SettingsDataStore` with the chronologically latest entry upon add/edit/delete, and record timestamped entries when baselines are saved in `ZonesSettingsActivity` or `EditSensorFieldDialog`.
  4. **Dedicated `AthleteProfileScreen`**:
     - Top summary & quick-entry cards for **Weight** (`kg`/`lbs` respecting `MyUnits`), **Resting HR** (`bpm`), **FTP** (`W`), and derived **Power-to-Weight (`W/kg`)** with latest measurement date.
     - Entry/Edit dialog with `DatePickerDialog` (defaulting to Today for new entries, supporting backdated entries and editing existing entries' value or date).
     - Interactive Compose `Canvas` time-series plot with metric selector (`Weight`, `Resting HR`, `FTP`, `W/kg`) and time-range filter (`1M`, `3M`, `6M`, `1Y`, `All`) using non-wrapping compact pills (Rule 38).
     - Chronological history log with edit and delete (protected by `DeleteConfirmationDialog`).
  5. **9-Language Localization Parity**: Localize all new strings across all 9 `values*/strings.xml` files with positional format specifiers (`%1$s`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Retroactive recalculation of completed historical workouts when historical FTP/HR/Weight entries are edited (live tracking and workout summaries continue reading active scalar flows from `SettingsDataStore`).
  - Cloud sync of body metrics to third-party health platforms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-391` (*Dedicated Athlete Profile Screen, Timestamped Body Metrics SQLite History, Derived W/kg Time-Series Plot & Bidirectional SettingsDataStore Synchronization*), complementing `REQ-UI-159` (*Single-Activity Compose Navigation*), `REQ-TRK-019` (*Athlete Weight Baseline & Power-to-Weight*), `REQ-UI-373` (*Automatic Zone Calculation Baselines*), and `REQ-UI-385` (*Streamlined ZonesSettingsActivity Header*).
* **Historical Origin & Commit Trace**:
  - `ATT-1083` (`REQ-UI-159`): Established the Single-Activity Compose Navigation Drawer (`AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`).
  - `ATT-1289` (`REQ-TRK-019`, commit `5eee662f`, Sprint `2026-42.4`): Introduced `KEY_ATHLETE_WEIGHT_KG` in `SettingsDataStore` and `AthleteWeightDialog.kt`.
  - `ATT-3188` (`REQ-UI-373`, commit `c800ca58`, Sprint `2026-42.4`): Introduced `KEY_CYCLING_FTP`, `KEY_CYCLING_FTP_UPDATED_AT`, and `KEY_HR_REST` in `SettingsDataStore`.
  - `ATT-3777` (`REQ-UI-385`, commit `5e0e50c3`, Sprint `2026-42.6`): Removed the temporary Athlete Weight row from the `ZonesSettingsActivity` header in preparation for the dedicated Athlete Profile screen (`ATT-3704`).
* **Root Reason for Existing Formulation**:
  - `SettingsDataStore` scalar flows (`athleteWeightKgFlow`, `hrRestFlow`, `ftpFlow`) were designed for low-latency reactive reads during 1-Hz live tracking (`CyclingPowerMetricsDevice`) and zone boundary calculation (`ZoneCalculatorDialog`).
* **Preservation of Core Invariants**:
  - Keeping `SettingsDataStore` as the active scalar source for live tracking while synchronizing it automatically with the chronologically latest entry in `AthleteMetricsRepository` preserves 100% compatibility with all existing live tracking, sensor field, and zone calculation pipelines.
  - Implementing symmetric `fromCursor(cursor)` and `fromCursorBatch(cursor)` on `AthleteMetricEntry` satisfies Rule 20 (`docs/coding_standards.md` §2.1).

---

## 5. Architectural Strategy & High-Level Solution

1. **Database & DTO Layer (`com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager.kt`)**:
   - Define `enum class AthleteMetricType { WEIGHT_KG, HR_REST_BPM, FTP_WATTS }`.
   - Define `data class AthleteMetricEntry(val id: Long = 0L, val timestampMs: Long, val metricType: AthleteMetricType, val value: Double)` with companion `fromCursor(cursor: Cursor): AthleteMetricEntry` and `fromCursorBatch(cursor: Cursor): List<AthleteMetricEntry>` (Rule 20).
   - Define `AthleteMetricsDbHelper(context: Context)` managing `AthleteMetrics.db` (version 1) with table `athlete_metrics_history` (`_id`, `timestamp_ms`, `metric_type`, `value`) and index `idx_athlete_metrics_type_time`.
   - Provide CRUD operations: `insertEntry`, `updateEntry`, `deleteEntry`, `getAllEntries`, `getEntriesByType`, and `getLatestEntryForType`.

2. **Repository & Time-Series Math Layer (`com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository.kt` & `AthleteMetricsPlotMath.kt`)**:
   - `AthleteMetricsRepository` coordinates `AthleteMetricsDatabaseManager` and `SettingsDataStore`:
     - `ensureSeededFromDataStore()`: One-time seed of existing positive Weight, Resting HR, and FTP values from `SettingsDataStore` when no entries exist for that metric type.
     - `addOrUpdateEntry(...)` & `deleteEntry(...)`: Persists to SQLite, refreshes the reactive `entriesFlow`, and synchronizes the chronologically latest entry for that metric type to `SettingsDataStore` (including clearing/resetting when the last entry of a type is deleted).
     - `recordBaselineFromSettings(metricType, value, timestampMs)`: Appends a timestamped entry when updated from `ZonesSettingsActivity` or `EditSensorFieldDialog`.
   - `AthleteMetricsPlotMath` (pure Kotlin math utility):
     - Filters entries by `MetricsTimeRange` (`ONE_MONTH`, `THREE_MONTHS`, `SIX_MONTHS`, `ONE_YEAR`, `ALL`).
     - Computes derived `W/kg` (`PlotMetricType.POWER_TO_WEIGHT`) series at each timestamp $t$ from the effective active FTP ($F(t)$) and active Weight ($W(t)$) at time $t$: $\text{wPerKg}(t) = F(t) / W(t)$.
     - Computes normalized chart coordinates $(x \in [0, 1], y \in [0, 1])$ and Y-axis domain bounds $[Y_{\min}, Y_{\max}]$ (applying `LBS_PER_KG` when `isImperial && metric == PlotMetricType.WEIGHT`).

3. **UI & Navigation Layer (`AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `NavRoutes.kt`, `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt`)**:
   - Add `R.id.drawer_athlete_profile`, `R.drawable.ic_athlete_profile`, `NavRoutes.ATHLETE_PROFILE`, and wire into `AppNavigationDrawer`, `NavRoutes`, and `ATrainingTrackerApp`.
   - Build `AthleteProfileScreen` following `docs/design_guidelines.md` §5:
     - `primaryContainer` header with `LayoutConstants.HEADER_TITLE_ROW_HEIGHT`, title, and `+` `IconButton`.
     - 2x2 summary grid cards (`RoundedCornerShape(12.dp)`) for Weight, Resting HR, FTP, and `W/kg`.
     - Interactive time-series chart card with compact non-wrapping metric and time-range selector pills (Rule 38) and a Compose `Canvas` plot.
     - Chronological history list with Edit (`AthleteMetricEntryDialog` + Material 3 `DatePickerDialog`) and Delete (`DeleteConfirmationDialog`).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing `SettingsDataStore`, `ZonesSettingsActivity`, and navigation unit tests.
  2. Symmetric `fromCursor` and `fromCursorBatch` DTO mapping (Rule 20).
  3. Non-wrapping single-line selector pills (`maxLines = 1, softWrap = false`, Rule 38) and 9-locale string parity with positional format specifiers (`%1$s`).
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Self-contained SQLite history database and Compose screen with clean bidirectional synchronization to existing `SettingsDataStore` flows.
