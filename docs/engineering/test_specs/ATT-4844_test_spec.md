# Stage 2: Requirement & Test Specification - ATT-4844: [Feature] Persist timestamped zone boundary snapshots per ZoneType and resolve historical workouts against the zones active at workout time

**Ticket**: [ATT-4844](https://rainerblind.atlassian.net/browse/ATT-4844) | **Sub-task**: Pending (`[Req & Test Spec]`) | **Parent Epic**: Standalone (`ATT-4844`)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2` | **Branch**: `feature/ATT-4844`  
**Requirement Mapping**: `REQ-SET-082` (*Timestamped Zone Boundary Snapshots & Historical Workout Zone Resolution*)  
**Test Spec ID**: `TST-SET-082` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-01  

---

## 1. Requirement Specification (`REQ-SET-082`)

### 1.1 Problem Statement & Rationale
Zone boundaries (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) are stored only as un-timestamped preferences in `SettingsDataStore`. Updating zones retroactively recalculates all historical workouts against modern cutoffs rather than the zones active when recorded. Storing timestamped atomic boundary vectors per `ZoneType` in `AthleteMetrics.db` guarantees historical fidelity across Workout Summaries, Details, and Telemetry Graphs.

### 1.2 Functional & Architectural Requirements
1. **Atomic Zone Vector Snapshot Persistence (`REQ-SET-082.1`)**:
   * Persist zone vectors in `AthleteMetrics.db` table `zone_history`: `(_id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp_ms INTEGER NOT NULL, zone_type TEXT NOT NULL, boundaries TEXT NOT NULL)` with index `idx_zone_history_type_time` on `(zone_type, timestamp_ms DESC, _id DESC)`.
   * Increment `AthleteMetricsDbHelper.DB_VERSION` to `2` with symmetric `onCreate`/`onUpgrade` parity (Rule 64) and direct `DB_VERSION` constant in `DatabaseSchemaRegistry.kt` (Rule 62).
   * Enforce strict ascending monotonicity ($Z_1 < Z_2 < \dots < Z_n$) with 4 cutoffs for `HR_RUN`/`HR_BIKE` and 6 for `PWR_BIKE`.
   * Seed initial baseline snapshots from `SettingsDataStore` in a single SQLite transaction on upgrade/fresh install.
2. **Unidirectional SettingsDataStore Projection (`REQ-SET-082.2`)**:
   * On any snapshot mutation, sync the latest snapshot (`max(timestamp_ms)`) to `SettingsDataStore` for $O(1)$ lock-free live cockpit telemetry evaluation.
3. **Historical Workout Zone Resolution (`REQ-SET-082.3`)**:
   * Provide `getEffectiveZoneBoundaries(zoneType, workoutStartTimeMs)` querying the latest snapshot with `timestamp_ms <= workoutStartTimeMs` (fallback to earliest recorded snapshot or defaults).
4. **Aftermath Analytics & Telemetry Integration (`REQ-SET-082.4`)**:
   * In `WorkoutRepository.kt`, resolve `getHeartRateZoneDistribution` and `getPowerZoneDistribution` using `getEffectiveZoneBoundaries`.
   * Forward effective thresholds to `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `TelemetryMetricGraph.kt` (zone bands, dashed threshold lines, scrubbing badges).
5. **Profile & Zones UI & Backdating (`REQ-SET-082.5`)**:
   * Normal edits stamp snapshots with `System.currentTimeMillis()`. Dedicated "History" dialog allows viewing, backdating, editing, and deleting snapshots with date selection and monotonicity validation.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Historical Resolution)**: Given snapshot $S_1$ at $t_1$ (FTP 250 W), workout $W_1$ at $t_2 > t_1$, and snapshot $S_2$ at $t_3 > t_2$ (FTP 284 W), When viewing $W_1$, Then Power zones and telemetry evaluate against $S_1$ ($250\text{ W}$), whereas workouts after $t_3$ evaluate against $S_2$ ($284\text{ W}$).
* **Criterion 2 (Backdating & Monotonicity)**: In Zone History, saving past-dated cutoffs persists at that timestamp and historical workouts resolve against it; saving non-monotonic cutoffs is rejected.

### 1.4 System Invariants
1. Monotonicity ($Z_1 < Z_2 < \dots < Z_n$) is strictly enforced on all mutations.
2. SQLite schema parity (`onCreate` == `onUpgrade`) in `AthleteMetrics.db` (Rule 64).
3. Live cockpit $O(1)$ latency via `SettingsDataStore` is preserved.

---

## 2. Test Specification (`TST-SET-082`)

### Test Case 1: Schema Migration & DatabaseSchemaRegistry Contract (`TST-SET-082.1`)
* **Scope**: DB Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/AthleteMetricsRealSqliteMigrationTest.kt`
* **Action**: Execute `onUpgrade` to v2; assert table/index parity with `onCreate` (Rule 64); assert `DatabaseSchemaRegistry.getMaxSupportedVersion("AthleteMetrics.db") == AthleteMetricsDbHelper.DB_VERSION` (Rule 62).

### Test Case 2: Zone Snapshot CRUD, Monotonicity & Symmetric DTO Mapping (`TST-SET-082.2`)
* **Scope**: DB Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsZoneSnapshotTest.kt`
* **Action**: Insert, query effective at timestamp, update, delete snapshots; assert non-monotonic vectors throw `IllegalArgumentException`; assert `fromCursor` and `fromCursorBatch` yield identical objects (Rule 20).

### Test Case 3: Unidirectional SettingsDataStore Projection (`TST-SET-082.3`)
* **Scope**: Repo Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsRepositorySyncTest.kt`
* **Action**: Insert new snapshot; assert `SettingsDataStore` updates; delete latest snapshot; assert rollback to previous snapshot.

### Test Case 4: Historical Workout Zone Distribution Resolution (`TST-SET-082.4`)
* **Scope**: Aftermath Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryHistoricalZoneTest.kt`
* **Action**: Given workouts at $t=1000$ and $t=3000$ and snapshots at $t=500$ (FTP 200W) and $t=2000$ (FTP 250W), assert Workout 1 uses 200W thresholds and Workout 2 uses 250W thresholds.

### Test Case 5: 9-Language Localization Parity (`TST-SET-082.5`)
* **Scope**: Localization Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Run `TranslationParityTest`; assert 100% parity across all 9 locales with zero missing entries and matching `%1$s` tokens.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-082.1` | DB Unit | `AthleteMetricsDbHelper.onUpgrade` | `REQ-SET-082.1` | Specified |
| `TST-SET-082.2` | DB Unit | `AthleteMetricsDatabaseManager.getEffectiveZoneBoundaries` | `REQ-SET-082.1`, `REQ-SET-082.3` | Specified |
| `TST-SET-082.3` | Repo Unit | `AthleteMetricsRepository.saveZoneSnapshot` | `REQ-SET-082.2` | Specified |
| `TST-SET-082.4` | Aftermath Unit | `WorkoutRepository.getPowerZoneDistribution` | `REQ-SET-082.3`, `REQ-SET-082.4` | Specified |
| `TST-SET-082.5` | Localization | `TranslationParityTest` | `REQ-SET-082.5` | Specified |
