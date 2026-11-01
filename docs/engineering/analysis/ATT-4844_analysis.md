# Stage 1 Analysis: ATT-4844 - Persist Timestamped Zone Boundary Snapshots & Resolve Historical Workouts

**Ticket**: [ATT-4844](https://rainerblind.atlassian.net/browse/ATT-4844)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: Standalone (`ATT-4844`)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4844`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Currently, training zone boundaries (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) are stored only as scalar, un-timestamped preferences in `SettingsDataStore.kt`. When an athlete updates their zones (e.g. after a fitness block or new FTP test), all past workouts in Workout Summaries and Workout Details are retroactively evaluated against *today's* zone boundaries. This distorts historical analysis: an interval workout executed at 250 W when FTP was 250 W (Zone 4) retroactively renders in Zone 3 if FTP later increased to 285 W. Past workouts must resolve against the zone boundaries that were active at `workoutStartTimeMs`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  * `SettingsDataStore.kt`: Holds single, un-versioned keys (`HR_RUN_ZONE*_MAX`, `HR_BIKE_ZONE*_MAX`, `PWR_BIKE_ZONE*_MAX`).
  * `WorkoutRepository.kt` (`getHeartRateZoneDistribution`, `getPowerZoneDistribution`, `loadWorkoutDetailTelemetry`): Resolves zones via `SettingsDataStoreJavaHelper.getZoneMax()` using current device settings regardless of `workoutId` or workout date.
  * `TelemetryMetricGraph.kt` & `MapDetailLayout.kt`: Fall back to `TelemetryZoneMath.loadHeartRateThresholds()` / `loadPowerThresholds()` fetching current preferences, ignoring historical workout dates.
* **Independent Hypothesis Verification (ATT-3534)**:
  * Verified: `AthleteMetrics.db` (`AthleteMetricsDatabaseManager.kt`) already supports timestamped physiological series (`WEIGHT_KG`, `HR_REST_BPM`, `FTP_WATTS`, `CP_WATTS`, `W_PRIME_KJ`) with `getEntryEffectiveAt(metricType, timestampMs)`. Extending this pattern with a dedicated `zone_history` table in `AthleteMetrics.db` provides an atomic, robust persistence layer.
  * Schema & Migration Invariant Audit: `AthleteMetricsDbHelper` is currently `DB_VERSION = 1`. In `DatabaseSchemaRegistry.kt:43`, `"AthleteMetrics.db" to 1` violates Rule 62 (hardcoded literal instead of `AthleteMetricsDbHelper.DB_VERSION`). Upgrading to `DB_VERSION = 2` must enforce Rule 62, Rule 64 (`onCreate`/`onUpgrade` parity), and Rule 66 (seed initial baseline from `SettingsDataStore`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. `AthleteMetrics.db` schema migration to `DB_VERSION = 2` with atomic `zone_history` table: `(id, timestamp_ms, zone_type, boundaries)` and index `idx_zone_history_type_time`.
  2. Rule 62 / 64 / 66 compliance: direct `DB_VERSION` constant in `DatabaseSchemaRegistry`, `onCreate`/`onUpgrade` parity test, and automatic upgrade-path baseline seeding from `SettingsDataStore`.
  3. Unidirectional synchronization: latest snapshot (`max(timestampMs)`) updates `SettingsDataStore` for zero-latency live cockpit access.
  4. Query API `getEffectiveZoneSnapshot(zoneType, workoutStartTimeMs)` returning the latest snapshot with `timestamp_ms <= workoutStartTimeMs` (fallback to earliest snapshot or DataStore defaults).
  5. Integration into aftermath pipeline: `WorkoutRepository` (`hrZoneDistribution`, `powerZoneDistribution`, `WorkoutDetailTelemetry`), `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `MapDetailLayout.kt` passing effective thresholds into `TelemetryMetricGraph`.
  6. UI in `Profile & Zones`: Saving records snapshot stamped with `now`; intuitive history dialog to view, backdate, edit, or delete past zone snapshots.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying live-tracking cockpit calculation engines (they continue using $O(1)$ in-memory flows).
  2. Modifying `WorkoutSummaries.db` or `WorkoutSamples.db` schemas.
  3. Modifying scalar physiological metrics or changing `SensorType` definitions.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: Net-new requirement `REQ-SET-082`, extending `REQ-UI-391` (Athlete Profile History) and `REQ-UI-428` (Atomic Zone Persistence).
* **Chesterton's Fence**: `REQ-UI-428` introduced atomic single-transaction persistence in `SettingsDataStore` for UI stability. Preserving `SettingsDataStore` as an $O(1)$ projected mirror of the latest snapshot ensures live tracking and background sensor loops incur zero database query overhead.

---

## 5. Architectural Strategy & High-Level Solution
* **Database & Repository Layer**:
  * `AthleteMetricsDatabaseManager.kt`: Increment `DB_VERSION = 2`. Create `zone_history` table (`_id`, `timestamp_ms`, `zone_type`, `boundaries`). Add `ZoneSnapshotEntry` with symmetric cursor mappers. Provide `getEffectiveZoneSnapshot(zoneType, timestampMs)`, `insertZoneSnapshot`, `updateZoneSnapshot`, `deleteZoneSnapshot`, and `seedZoneSnapshotsInTransaction`.
  * `AthleteMetricsRepository.kt`: Seed initial snapshots from `SettingsDataStore` on upgrade. Expose `zoneSnapshotsFlow` and sync latest snapshot per `ZoneType` to `SettingsDataStore`.
* **Aftermath Zone Resolution**:
  * `WorkoutRepository.kt`: Resolve workout start timestamp (`workoutData.startTimeS * 1000L`). Query effective thresholds via `getEffectiveZoneSnapshot`. Populate `WorkoutDetailTelemetry` with effective thresholds.
  * `TelemetryMetricGraph.kt`, `WorkoutSummary.kt`, `MapDetailLayout.kt`: Forward effective thresholds to graph rendering and scrubbing badges.
* **UI**:
  * `ZonesSettingsViewModel.kt`: Commits trigger `saveZoneSnapshot` (`timestampMs = now`).
  * `ZoneProfileTabContent.kt`: Add "History" action button launching history management dialog (view past snapshots, backdate/edit/delete).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Monotonicity: Zone cutoffs must strictly satisfy $Z_1 < Z_2 < \dots < Z_n$ before saving.
  2. Schema Parity: Clean `onCreate` and `onUpgrade` equivalence in `AthleteMetrics.db` guarded by contract tests (Rules 62, 64).
  3. Cockpit Performance: $O(1)$ lock-free live cockpit telemetry evaluation via `SettingsDataStore` is preserved.
* **Risk Rating**: **MEDIUM** — Touches database migration (`AthleteMetrics.db`), aftermath visualization pipelines, and zone settings UI. Mitigated by comprehensive contract and migration unit tests.
