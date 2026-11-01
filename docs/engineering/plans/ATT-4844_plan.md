# Stage 3: Implementation Plan - ATT-4844: Persist Timestamped Zone Boundary Snapshots & Resolve Historical Workouts

**Ticket**: [ATT-4844](https://rainerblind.atlassian.net/browse/ATT-4844) | **Sub-task**: Pending (`[Impl-Plan]`) | **Parent Epic**: Standalone (`ATT-4844`)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2` | **Branch**: `feature/ATT-4844`  
**Requirement Mapping**: `REQ-SET-082` | **Test Mapping**: `TST-SET-082` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-01  

---

## 1. Problem Description & Background
Zone cutoffs (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) are currently un-timestamped in `SettingsDataStore`. Updating boundaries retroactively recalculates all past workouts against modern numbers. We will add a timestamped `zone_history` table in `AthleteMetrics.db` (v1 $\rightarrow$ v2), seed baseline data from `SettingsDataStore`, maintain $O(1)$ live DataStore projection, resolve historical workouts at `workoutStartTimeMs`, and add a zone history/backdating UI.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-082` (`REQ-SET-082.1`..`5`)
* **Test Mapping**: `TST-SET-082` (`TST-SET-082.1`..`5`)
* **Coding Standards**: Rule 20 (Symmetric Cursor DTO), Rule 62 (`DatabaseSchemaRegistry` constant + `@JvmOverloads`), Rule 64 (`onCreate`/`onUpgrade` parity), Rule 66 (Upgrade-path seeding, test isolation, no state writes in composition).

---

## 3. System Invariants & Preserved Behavior
1. **Monotonicity**: $Z_1 < Z_2 < \dots < Z_n$ strictly validated on all snapshot writes (4 bounds for HR, 6 for Power).
2. **Schema Parity & Registry Truth**: `onCreate` and `onUpgrade` produce identical schemas; `DatabaseSchemaRegistry` references `AthleteMetricsDbHelper.DB_VERSION` directly (Rules 62 & 64).
3. **Live Telemetry Zero-Latency**: Cockpit hot-paths continue reading $O(1)$ in-memory `SettingsDataStore` flows.
4. **Upgrade-Path Zero Data Loss**: Existing installations seed initial snapshots from DataStore baselines (Rule 66).

---

## 4. Proposed Architectural Changes
* **Database Layer (`AthleteMetrics.db`)**: Increment `DB_VERSION = 2`. Create `zone_history` table (`_id`, `timestamp_ms`, `zone_type`, `boundaries`) and index `idx_zone_history_type_time`. Add `ZoneSnapshotEntry` with symmetric `fromCursor`/`fromCursorBatch`. Provide `getEffectiveZoneSnapshot(zoneType, timestampMs)`, `getLatestZoneSnapshot`, `insertZoneSnapshot`, `updateZoneSnapshot`, `deleteZoneSnapshot`, `seedZoneSnapshotsInTransaction`.
* **Repository & DataStore Sync**: `AthleteMetricsRepository` ensures initial seeding via DataStore flows, projects latest snapshot (`max(timestamp_ms)`) to `SettingsDataStore`, and handles rollback on deletion.
* **Historical Workout Resolution**: In `WorkoutRepository.kt`, read `workoutData.startTimeS * 1000L` and query `getEffectiveZoneSnapshot` for `getHeartRateZoneDistribution`, `getPowerZoneDistribution`, and `loadWorkoutDetailTelemetry`. Forward effective thresholds into `WorkoutDetailTelemetry`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `TelemetryMetricGraph.kt`.
* **UI & Consistency**:
  * Reference screen: `AthleteProfileScreen.kt` history tab and `AthleteMetricEntryDialog.kt`.
  * Reused components: `AlertDialog`, `OutlinedTextField`, `DatePickerDialog`, `TTColor`, `RoundedCornerShape(12.dp)`. No one-off styles.
  * In `ZonesSettingsViewModel.kt` (@JvmOverloads constructor per Rule 62): normal edits trigger `saveZoneSnapshot(timestampMs = now)`. History dialog manages past snapshots with reactive StateFlow (no writes during composition, Rule 66).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Migration, DTO & Registry (Rule 20, 62, 64)
* Files: `AthleteMetricsDatabaseManager.kt`, `DatabaseSchemaRegistry.kt`
* Implement `zone_history` table, `idx_zone_history_type_time`, `ZoneSnapshotEntry` (`fromCursor`/`fromCursorBatch`), `getEffectiveZoneSnapshot`, CRUD, and `DB_VERSION = 2`. Update `DatabaseSchemaRegistry` to reference `AthleteMetricsDbHelper.DB_VERSION`.

### Step 2: Repository Baseline Seeding & Unidirectional Projection (Rule 66)
* Files: `AthleteMetricsRepository.kt`, `SettingsDataStore.kt`
* Implement `seedZoneSnapshotsInTransaction` from current DataStore boundaries. Unidirectionally project latest snapshot to `SettingsDataStore` on mutation/deletion.

### Step 3: Historical Workout Resolution in Aftermath & Telemetry
* Files: `WorkoutRepository.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutSummary.kt`, `TelemetryMetricGraph.kt`
* Resolve effective thresholds at `workoutStartTimeMs` for distribution calculations, graph zone bands, dashed lines, and scrubbing tags.

### Step 4: Profile & Zones History Dialog & Backdating UI
* Files: `ZonesSettingsViewModel.kt`, `ZoneProfileTabContent.kt`, `ZoneHistoryDialog.kt`
* Add "History" button in zone tabs; show dialog with timestamped snapshot list, backdated entry creation, edit, and deletion with monotonicity validation.

### Step 5: Contract, Migration & Verification Unit Tests (Rule 66)
* Files: `AthleteMetricsRealSqliteMigrationTest.kt`, `AthleteMetricsZoneSnapshotTest.kt`, `AthleteMetricsRepositorySyncTest.kt`, `WorkoutRepositoryHistoricalZoneTest.kt`
* Unit tests for schema parity, DTO mapping, repository sync, and historical resolution. Isolated DataStore via `TemporaryFolder`.

---

## 6. Verification & Test Plan
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.AthleteMetricsRealSqliteMigrationTest"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetrics*"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryHistoricalZoneTest"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* `python3 tools/verify_requirement_governance.py`
