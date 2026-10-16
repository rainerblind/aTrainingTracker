# Stage 1 Analysis: ATT-3704 - Add dedicated Athlete Profile screen with timestamped history and time-series plots for Weight, Resting HR, FTP, and W/kg

**Ticket**: [ATT-3704](https://rainerblind.atlassian.net/browse/ATT-3704)  
**Sub-task**: [ATT-3830](https://rainerblind.atlassian.net/browse/ATT-3830) (`[Analysis]`)  
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
  - `KEY_HR_REST` (`Int`, bounds `30..120 bpm` per `ZoneCalculationMath.MIN_HR_REST..MAX_HR_REST`)
  - `KEY_CYCLING_FTP` (`Int`, bounds `50..600 W` per `ZoneCalculationMath.MIN_FTP..MAX_FTP`) and `KEY_CYCLING_FTP_UPDATED_AT` (`Long`)
- Live tracking calculations (`BANALService`, `CyclingPowerMetricsDevice`, `ZonesSettingsActivity`, `EditSensorFieldDialog`) subscribe to `athleteWeightKgFlow`, `hrRestFlow`, `ftpFlow`, and `ftpUpdatedAtFlow`.

### 2.2 Single Source of Truth vs. Circular Sync Prevention
- A naive bidirectional design where `SettingsDataStore` triggers `AthleteMetricsRepository` AND `AthleteMetricsRepository` triggers `SettingsDataStore` would risk circular write loops, race conditions, and break existing pure-JVM unit tests that instantiate `SettingsDataStore(mockContext, testDataStore)`.
- **Solution — Unidirectional Single Source of Truth Architecture**:
  1. **`AthleteMetricsRepository` is the Single Source of Truth** for all athlete body/physiological metric mutations.
  2. **`SettingsDataStore` is strictly a downstream reactive projection/cache** of the chronologically latest entry per metric type (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`). `SettingsDataStore` NEVER invokes `AthleteMetricsRepository`.
  3. All UI mutation call sites (`AthleteProfileScreen`, `ZonesSettingsActivity`, and `EditSensorFieldDialog`) route their baseline saves through `AthleteMetricsRepository` (guarded by a coroutine `Mutex` on `Dispatchers.IO`), which writes to SQLite first and then projects the authoritative latest value into `SettingsDataStore`.

### 2.3 Explicit State-Machine Transitions for Latest-Value Projection
For any metric type $M \in \{\text{WEIGHT\_KG}, \text{HR\_REST\_BPM}, \text{FTP\_WATTS}\}$, after any SQLite mutation (`insert`, `update`, `delete`), `AthleteMetricsRepository.syncLatestToDataStore(M)` queries:
```sql
SELECT _id, timestamp_ms, metric_type, value
FROM athlete_metrics_history
WHERE metric_type = ?
ORDER BY timestamp_ms DESC, _id DESC
LIMIT 1
```
and executes the following deterministic state transition:

| Mutation Event | Resulting `latest` Row | `SettingsDataStore` Projection Action |
| :--- | :--- | :--- |
| **Insert / Edit** with $t \ge t_{\text{prev\_latest}}$ | New/edited row is `latest` | Updates `SettingsDataStore` scalar (`KEY_ATHLETE_WEIGHT_KG`, `KEY_HR_REST`, or `KEY_CYCLING_FTP` + `KEY_CYCLING_FTP_UPDATED_AT`) to `latest.value` (and `latest.timestampMs`). |
| **Insert / Edit** of backdated row with $t < t_{\text{prev\_latest}}$ | Existing newest row remains `latest` | `SettingsDataStore` retains `latest.value` unchanged (backdated historical entries never overwrite newer current baselines). |
| **Delete** of current `latest` row (when $\ge 1$ older row remains) | Next most recent row becomes `latest` | Reverts `SettingsDataStore` scalar to the next most recent remaining row's `value` and `timestampMs`. |
| **Delete** of the **last remaining** row for metric $M$ | `latest == null` | Clears/resets the scalar in `SettingsDataStore` (`KEY_ATHLETE_WEIGHT_KG = 0.0f`, `prefs.remove(KEY_HR_REST)`, or `prefs.remove(KEY_CYCLING_FTP)` + `prefs.remove(KEY_CYCLING_FTP_UPDATED_AT)`). |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Navigation Drawer & Route Wiring**: Add `R.id.drawer_athlete_profile` under `R.string.drawer__my_stuff` in `AppNavigationDrawer.kt`, `NavRoutes.ATHLETE_PROFILE = "athlete_profile"` in `NavRoutes.kt`, and `composable(NavRoutes.ATHLETE_PROFILE)` in `ATrainingTrackerApp.kt`.
  2. **SQLite Persistence, Bounds Check & Symmetric DTO Mapping (Rule 20)**: Create `AthleteMetricsDatabaseManager.kt` (`AthleteMetricsDbHelper` with SQLite `CHECK(value > 0)` and application-layer bounds enforcement, `AthleteMetricType` enum, `AthleteMetricEntry` with symmetric `fromCursor` and `fromCursorBatch` mappers).
  3. **Transactional One-Time Initial Seeding & Unidirectional Projection**: Seed initial history entries inside an explicit SQLite transaction (`beginTransaction` / `setTransactionSuccessful` / `endTransaction`) from existing `SettingsDataStore` baselines (`athleteWeightKgFlow`, `hrRestFlow`, `ftpFlow` + `ftpUpdatedAtFlow`), and project the chronologically latest entry into `SettingsDataStore` upon every add/edit/delete.
  4. **Dedicated `AthleteProfileScreen`**:
     - Top summary & quick-entry cards for **Weight** (`%.1f kg` / `%.1f lbs` respecting `MyUnits`), **Resting HR** (`%d bpm`), **FTP** (`%d W`), and derived **Power-to-Weight (`%.2f W/kg`)** with locale-formatted measurement date (`DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())`).
     - Entry/Edit dialog with Material 3 `DatePickerDialog` (defaulting to Today for new entries, supporting backdated entries and editing existing entries' value or date with locale-aware date formatting).
     - Interactive Compose `Canvas` time-series plot with metric selector (`Weight`, `Resting HR`, `FTP`, `W/kg`) and time-range filter (`1M`, `3M`, `6M`, `1Y`, `All`) using non-wrapping compact pills (Rule 38).
     - Chronological history log with edit and delete (protected by `DeleteConfirmationDialog`).
  5. **9-Language Localization Parity**: Localize all new strings across all 9 `values*/strings.xml` files with positional format specifiers (`%1$s`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Retroactive recalculation of completed historical workouts when historical FTP/HR/Weight entries are edited.
  - Cloud sync of body metrics to third-party health platforms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-391` (*Dedicated Athlete Profile Screen, Timestamped Body Metrics SQLite History, Derived W/kg Time-Series Plot & Unidirectional SettingsDataStore Projection*), complementing `REQ-UI-159`, `REQ-TRK-019`, `REQ-UI-373`, and `REQ-UI-385`.
* **Historical Origin & Commit Trace**:
  - `ATT-1083` (`REQ-UI-159`): Established the Single-Activity Compose Navigation Drawer (`AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`).
  - `ATT-1289` (`REQ-TRK-019`, commit `5eee662f`, Sprint `2026-42.4`): Introduced `KEY_ATHLETE_WEIGHT_KG` in `SettingsDataStore` (`30.0..300.0 kg`, `LBS_PER_KG = 2.20462262185f`) and `2`-decimal `W/kg` formatting (`%.2f`).
  - `ATT-3188` (`REQ-UI-373`, commit `c800ca58`, Sprint `2026-42.4`): Introduced `KEY_CYCLING_FTP`, `KEY_CYCLING_FTP_UPDATED_AT`, and `KEY_HR_REST` in `SettingsDataStore`.
  - `ATT-3777` (`REQ-UI-385`, commit `5e0e50c3`, Sprint `2026-42.6`): Removed the temporary Athlete Weight row from the `ZonesSettingsActivity` header in preparation for `ATT-3704`.
* **Root Reason for Existing Formulation**:
  - `SettingsDataStore` scalar flows (`athleteWeightKgFlow`, `hrRestFlow`, `ftpFlow`) were designed for low-latency reactive reads during 1-Hz live tracking (`CyclingPowerMetricsDevice`) and zone boundary calculation (`ZoneCalculatorDialog`).
* **Preservation of Core Invariants**:
  - Keeping `SettingsDataStore` decoupled from `AthleteMetricsRepository` while projecting the chronologically latest SQLite entry from `AthleteMetricsRepository -> SettingsDataStore` guarantees zero circular dependencies, preserves 100% compatibility with all existing unit tests, and maintains `REQ-TRK-019` `W/kg` precision (`Double` division formatted to `%.2f`).

---

## 5. Architectural Strategy & High-Level Solution

1. **Database, Bounds Validation & DTO Layer (`com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager.kt`)**:
   - Define `enum class AthleteMetricType(val minMetricValue: Double, val maxMetricValue: Double)`:
     - `WEIGHT_KG(30.0, 300.0)`
     - `HR_REST_BPM(30.0, 120.0)`
     - `FTP_WATTS(50.0, 600.0)`
   - Define `data class AthleteMetricEntry(val id: Long = 0L, val timestampMs: Long, val metricType: AthleteMetricType, val value: Double)` with companion `fromCursor(cursor: Cursor): AthleteMetricEntry` and `fromCursorBatch(cursor: Cursor): List<AthleteMetricEntry>` (Rule 20).
   - Define `AthleteMetricsDbHelper(context: Context)` managing `AthleteMetrics.db` (version 1) with table `athlete_metrics_history` (`_id INTEGER PRIMARY KEY AUTOINCREMENT`, `timestamp_ms INTEGER NOT NULL`, `metric_type TEXT NOT NULL`, `value REAL NOT NULL CHECK(value > 0)`) and index `idx_athlete_metrics_type_time`.
   - Provide `seedEntriesInTransaction(entries: List<AthleteMetricEntry>)` using `db.beginTransaction()` / `db.setTransactionSuccessful()` / `db.endTransaction()` so initial migration is atomic and rollback-safe.

2. **Repository, Precision & Time-Series Math Layer (`com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository.kt` & `AthleteMetricsPlotMath.kt`)**:
   - `AthleteMetricsRepository` (guarded by `Mutex`) is the single source of truth:
     - `ensureSeededFromDataStore()`: Atomically seeds existing positive Weight, Resting HR, and FTP values from `SettingsDataStore` inside a single SQLite transaction when `KEY_ATHLETE_METRICS_SEEDED` is false and the table is empty, then sets `KEY_ATHLETE_METRICS_SEEDED = true`.
     - `addEntry`, `updateEntry`, `deleteEntry`: Validates bounds `[metricType.minMetricValue, metricType.maxMetricValue]`, persists to SQLite, refreshes `entriesFlow`, and projects the latest entry (or clears when empty) to `SettingsDataStore`.
   - `AthleteMetricsPlotMath` (pure Kotlin math utility):
     - Filters entries by `MetricsTimeRange` (`ONE_MONTH`, `THREE_MONTHS`, `SIX_MONTHS`, `ONE_YEAR`, `ALL`).
     - Computes derived `W/kg` (`PlotMetricType.POWER_TO_WEIGHT`) in `Double` precision at each timestamp $t$ from the effective active FTP ($F(t)$) and active Weight ($W(t)$) at time $t$: $\text{wPerKg}(t) = F(t) / W(t)$, formatted to **2 decimal places** (`%.2f W/kg`) per `REQ-TRK-019`.
     - Formats dates via `DateFormat.getDateInstance(DateFormat.MEDIUM, locale)` for cards/history rows and `DateFormat.getDateInstance(DateFormat.SHORT, locale)` for chart axis ticks.

3. **UI & Navigation Layer (`AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `NavRoutes.kt`, `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt`)**:
   - Add `R.id.drawer_athlete_profile`, `R.drawable.ic_athlete_profile`, `NavRoutes.ATHLETE_PROFILE`, and wire into `AppNavigationDrawer`, `NavRoutes`, and `ATrainingTrackerApp`.
   - Build `AthleteProfileScreen` following `docs/design_guidelines.md` §5 with 2x2 summary cards, interactive time-series `Canvas` plot, and chronological history list with `DatePickerDialog` and `DeleteConfirmationDialog`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Unidirectional data flow (`AthleteMetricsRepository -> SettingsDataStore`) with zero circular dependencies and zero regressions in existing `SettingsDataStore` unit tests.
  2. Symmetric `fromCursor` and `fromCursorBatch` DTO mapping (Rule 20) and atomic SQLite transaction for initial seeding.
  3. Non-wrapping single-line selector pills (`maxLines = 1, softWrap = false`, Rule 38), locale-aware date formatting (`DateFormat.getDateInstance`), `2`-decimal `W/kg` precision (`REQ-TRK-019`), and 9-locale string parity.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM**
  - Introduces a new SQLite history table and repository synchronized with `SettingsDataStore`, mitigated by unidirectional projection, `Mutex` serialization, explicit state-machine fallback on delete, and atomic transactional seeding.
