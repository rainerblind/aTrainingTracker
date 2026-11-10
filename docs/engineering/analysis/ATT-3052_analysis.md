# Stage 1 Analysis: ATT-3052 - Synchronize completed workouts and telemetry series to Android Health Connect

**Ticket**: [ATT-3052](https://rainerblind.atlassian.net/browse/ATT-3052)  
**Sub-task**: [ATT-6434](https://rainerblind.atlassian.net/browse/ATT-6434) (`[Analysis]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-3052`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Athletes using aTrainingTracker currently rely on file exports (TCX/FIT) or third-party cloud uploads (Strava, Dropbox, Google Drive) to share workout data with external fitness ecosystems. With Google Fit API deprecation at the end of 2026, Android Health Connect (`androidx.health.connect:connect-client`) has become the official Android standard for health and fitness interoperability. Integrating Health Connect enables aTrainingTracker to serve as a first-class workout data provider, synchronizing completed workouts and rich sensor telemetry directly to the secure on-device store without vendor lock-in.

---

## 2. Root Cause & Independent Architectural Gap Analysis (4-Augen-Prinzip)
* **Current State**: `ExportManager` orchestrates file exports and cloud upload workers (`StravaUploader`, `GoogleDriveUploader`), but lacks an on-device health record provider interface.
* **Independent Peer Review & Verification**:
  - *Telemetry Extraction*: `WorkoutSamplesDatabaseManager` stores per-second sensor telemetry (`SensorType.LATITUDE`, `LONGITUDE`, `ALTITUDE`, `HR`, `POWER`, `SPEED_mps`, `CADENCE`, `CALORIES`) in workout-specific tables (`WorkoutSamples.db`). These can be read via existing cursor iteration patterns established in `FitFileWriter.java`.
  - *Sport Type Mapping*: `BSportType` maps cleanly to Health Connect `ExerciseSessionRecord.EXERCISE_TYPE_*` (`BIKE` $\to$ `EXERCISE_TYPE_BIKING`, `RUN` $\to$ `EXERCISE_TYPE_RUNNING`, indoor/trainer $\to$ `EXERCISE_TYPE_BIKING_STATIONARY` / `EXERCISE_TYPE_RUNNING_TREADMILL`, others $\to$ `EXERCISE_TYPE_OTHER_WORKOUT`).
  - *Trackless & Indoor Support*: Indoor sessions lack GPS coordinates (`ExerciseRoute`); series records (`HeartRateRecord`, `PowerRecord`, `SpeedRecord`, `DistanceRecord`, `TotalCaloriesBurnedRecord`) must be emitted independently of route presence.
  - *Write Idempotency*: Health Connect `Metadata(clientRecordId = "workout_${fileBaseName}")` natively deduplicates insertions, guaranteeing safe re-syncs without duplicate sessions.
  - *Isolated Shard & Zero-Conflict Surface*: Shard `req_settings_profiles.md` (`REQ-SET-100`..`103`) is 100% disjoint from concurrent sprint lanes. No changes to `MainActivityWithNavigation.kt` or file writers needed.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Add `androidx.health.connect:connect-client:1.1.0` dependency and manifest permissions/intents (Rationale & Usage).
  - Implement `HealthConnectManager` handling SDK status, permission requests, and series record conversion.
  - Implement automatic background sync upon workout finalization and manual trigger from Workout Details / Edit Workout.
  - Provide Cloud Integrations settings toggle and permission flow in `HealthConnectSettingsDialog`.
  - Add localized resources in dedicated `app/src/main/res/values*/strings_health_connect.xml` across all 9 locales.
* **Out-of-Scope Non-Goals**: Modifying legacy file writers (`TCXFileWriter`, `FitFileWriter`), reading health records from other apps (write-only provider scope), or syncing historical workouts in bulk.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-New Requirements**: Introduces `REQ-SET-100` (*Health Connect Integration & Permissions*), `REQ-SET-101` (*Exercise Session & Telemetry Series Mapping*), `REQ-SET-102` (*Trackless Indoor Workout Parity*), and `REQ-SET-103` (*Health Connect Settings & Idempotent Sync*), verified by `TST-SET-100`..`TST-SET-103`.
* **No Existing Requirements Modified**: Supplements existing cloud export ecosystem (`REQ-SET-050`, `REQ-SET-055`) without altering Strava, Dropbox, or local file export invariants.

---

## 5. Architectural Strategy & High-Level Solution
* **Core Engine**: Create `HealthConnectManager` in package `com.atrainingtracker.trainingtracker.cloud.healthconnect`.
* **Data Mapping**: Convert `WorkoutData` and `WorkoutSamplesDbHelper` cursor rows into `ExerciseSessionRecord`, `ExerciseRoute`, `HeartRateRecord`, `PowerRecord`, `SpeedRecord`, `DistanceRecord`, `ElevationGainedRecord`, and `TotalCaloriesBurnedRecord`.
* **Lifecycle Wiring**: Invoke `HealthConnectManager.syncWorkout(workoutData)` upon workout completion in `WorkoutRepository` / `TrackerService` when enabled and permitted.
* **UI Layer**: Add `HealthConnectSettingsDialog` invoked via `NavRoutes` and manual sync action in `WorkoutHeader` / `EditWorkoutScreen`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero impact on tracking performance or database latency (Health Connect writes run on `Dispatchers.IO`).
  2. Trackless indoor workouts must sync successfully without GPS route requirements or NPEs.
  3. Safe failure handling: missing permissions or unavailable SDK must fail silently/gracefully without crash.
  4. Zero modifications to files locked by parallel lanes (`MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, etc.).
* **Risk Rating**: **MEDIUM** (Introduces new Android Jetpack library, runtime permission contracts, and IPC data model conversion; isolated from core tracking loop).
